package org.nineml.coffeesacks;

import net.sf.saxon.Controller;
import net.sf.saxon.event.ComplexContentOutputter;
import net.sf.saxon.event.NamespaceReducer;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.hof.UserFunctionReference;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.*;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.str.StringView;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.Untyped;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.SequenceExtent;
import org.nineml.coffeefilter.ParserOptions;
import org.nineml.coffeefilter.util.EventBuilder;
import org.nineml.coffeegrinder.parser.*;
import org.nineml.coffeegrinder.util.ParserAttribute;

import java.util.*;

public class AlternativeEventBuilder extends EventBuilder {
    public static final String ELLIPSIS = "…";
    protected final Processor processor;
    protected final Stack<StackFrame> symbolStack = new Stack<>();
    protected XPathContext context = null;
    protected UserFunctionReference.BoundUserFunction chooseAlternatives = null;

    public AlternativeEventBuilder(Processor processor, String ixmlVersion, ParserOptions options) {
        super(ixmlVersion, options);
        this.processor = processor;
    }

    public void setChooseAlternative(XPathContext context, UserFunctionReference.BoundUserFunction chooseFunction) {
        this.context = context;
        chooseAlternatives = chooseFunction;
    }

    @Override
    public int startAlternative(ForestNode tree, List<RuleChoice> ruleAlternatives) {
        int selected = super.startAlternative(tree, ruleAlternatives);

        if (chooseAlternatives != null) {
            // Reorder the alternatives so that the selected choice is always first
            final List<RuleChoice> alternatives;
            if (selected == 0) {
                alternatives = ruleAlternatives;
            } else {
                alternatives = new ArrayList<>();
                alternatives.add(ruleAlternatives.get(selected));
                int count = 0;
                for (RuleChoice alt : ruleAlternatives) {
                    if (count != selected) {
                        alternatives.add(alt);
                    }
                    count++;
                }
            }

            try {
                List<XdmNode> xmlAlternatives = xmlAlternatives(tree, alternatives);
                ArrayList<NodeInfo> nodeAlternatives = new ArrayList<>();
                for (XdmNode node : xmlAlternatives) {
                    nodeAlternatives.add(node.getUnderlyingNode());
                }
                Sequence result = chooseAlternatives.call(context, new Sequence[] { SequenceExtent.makeSequenceExtent(nodeAlternatives) });

                // I checked the return type when I loaded the function, so I think this is safe
                long value = ((Int64Value) result.head()).longValue();
                if (value != 0) {
                    if (value < 0 || value > alternatives.size()) {
                        throw new IllegalArgumentException("Value out of range from choose-alternatives function");
                    }
                    return ((int) value) - 1;
                }
            } catch (XPathException err) {
                throw new RuntimeException(err);
            }
        }

        return selected;
    }

    @Override
    public void startTree() {
        super.startTree();
        symbolStack.clear();
    }

    @Override
    public void startNonterminal(NonterminalSymbol symbol, Map<String,String> attributes, int leftExtent, int rightExtent) {
        super.startNonterminal(symbol, attributes, leftExtent, rightExtent);
        if (!symbolStack.isEmpty()) {
            StackFrame top = symbolStack.peek();
            if (!top.childCounts.containsKey(symbol)) {
                top.childCounts.put(symbol, 0);
            }
            top.childCounts.put(symbol, top.childCounts.get(symbol)+1);
        }
        symbolStack.push(new StackFrame(symbol, attributes, leftExtent, rightExtent));
    }

    @Override
    public void endNonterminal(NonterminalSymbol symbol, Map<String,String> attributes, int leftExtent, int rightExtent) {
        super.endNonterminal(symbol, attributes, leftExtent, rightExtent);
        symbolStack.pop();
    }

    public List<XdmNode> xmlAlternatives(ForestNode tree, List<RuleChoice> alternatives) throws CoffeeSacksException {
        ArrayList<XdmNode> contexts = new ArrayList<>();

        for (int altindex = 0; altindex < alternatives.size(); altindex++) {
            TreeWriter writer = new TreeWriter();
            writer.startDocument();
            int depth = 0;
            boolean rootChoice = symbolStack.size() > 0;

            for (int index = 1; index < symbolStack.size(); index++) {
                StackFrame frame = symbolStack.get(index);
                depth++;
                int count = symbolStack.get(index-1).childCounts.get(frame.symbol);
                HashMap<String, XdmAtomicValue> attributes = new HashMap<>();

                for (int sibling = 1; sibling < count; sibling++) {
                    writer.startSymbol(frame.symbol, attributes, false);
                    writer.endElement();
                }

                writer.startSymbol(frame.symbol, attributes);
            }

            // N.B. Although technically tree can be null, it will never be null for an iXML
            // grammar because there's always a single, unambiguous root
            if (tree.symbol != null) {
                depth++;

                // Java numbers the alternatives from 0, XSLT from 1, so add one
                int count = 0;
                if (symbolStack.get(symbolStack.size()-1).childCounts.containsKey(tree.symbol)) {
                    count = symbolStack.get(symbolStack.size()-1).childCounts.get(tree.symbol);
                }

                HashMap<String, XdmAtomicValue> attributes = new HashMap<>();
                for (int sibling = 0; sibling < count; sibling++) {
                    writer.startSymbol(tree.symbol, attributes, false);
                    writer.endElement();
                }

                attributes.put("from", new XdmAtomicValue(tree.leftExtent));
                attributes.put("to", new XdmAtomicValue(tree.rightExtent));
                attributes.put("alternative", new XdmAtomicValue(altindex+1));
                writer.startSymbol(tree.symbol, attributes);
            } else {
                throw new CoffeeSacksException(CoffeeSacksException.ERR_TREE_CONSTRUCTION, "Unexpected state, tree.symbol is null");
            }

            RuleChoice choice = alternatives.get(altindex);
            ForestNode left = choice.getLeftNode();
            ForestNode right = choice.getRightNode();

            if (right == null) {
                // ε
            } else {
                if (left != null) {
                    // Left and right side
                    if (left.state == null) {
                        HashMap<String,XdmAtomicValue> attributes = new HashMap<>();
                        attributes.put("from", new XdmAtomicValue(left.leftExtent));
                        attributes.put("to", new XdmAtomicValue(left.rightExtent));
                        writer.startSymbol(left.getSymbol(), attributes);
                    } else {
                        showXmlState(writer, left, rootChoice ? altindex + 1 : 0);
                    }
                }
                if (right.state == null) {
                    HashMap<String,XdmAtomicValue> attributes = new HashMap<>();
                    attributes.put("from", new XdmAtomicValue(right.leftExtent));
                    attributes.put("to", new XdmAtomicValue(right.rightExtent));
                    writer.startSymbol(right.getSymbol(), attributes);
                } else {
                    showXmlState(writer, right, rootChoice ? altindex + 1 : 0);
                }
            }

            while (depth > 0) {
                writer.endElement();
                depth--;
            }

            XdmNode doc = writer.endDocument();

            try {
                XdmNode context = null;

                XPathCompiler compiler = processor.newXPathCompiler();
                XPathExecutable exec = compiler.compile("//*[@alternative]");
                XPathSelector selector = exec.load();
                selector.setContextItem(doc);
                for (XdmValue value : selector.evaluate()) {
                    context = (XdmNode) value;
                }

                assert context != null;
                contexts.add(context);
            } catch (SaxonApiException err) {
                throw new CoffeeSacksException(CoffeeSacksException.ERR_TREE_CONSTRUCTION, "Internal error: failed to find @alternative in tree construction", err);
            }
        }

        return contexts;
    }

    private void showXmlState(TreeWriter writer, ForestNode node, int altindex) throws CoffeeSacksException {
        State state = node.getState();
        HashMap<String,XdmAtomicValue> attributes = new HashMap<>();
        attributes.put("from", new XdmAtomicValue(node.leftExtent));
        attributes.put("to", new XdmAtomicValue(node.rightExtent));
        writer.startSymbol(state.symbol, attributes);
        if (state.position > 0) {
            attributes.clear();
            StringBuilder literal = new StringBuilder();
            ParserAttribute litmark = null;
            for (int pos = 0; pos < state.position; pos++) {
                Symbol symbol = state.rhs.get(pos);
                if (symbol instanceof TerminalSymbol) {
                    TerminalSymbol ts = (TerminalSymbol) symbol;
                    ParserAttribute mark = ts.getAttribute("tmark");
                    if (literal.length() == 0 || litmark.equals(mark)) {
                        litmark = mark;
                        literal.append(ts.getToken().getValue());
                    } else {
                        writer.addLiteral(literal, litmark.getValue());
                        litmark = mark;
                        literal.append(ts.getToken().getValue());
                    }
                } else {
                    if (literal.length() > 0) {
                        writer.addLiteral(literal, litmark.getValue());
                    }
                    writer.startSymbol(symbol, attributes);
                    writer.addText(ELLIPSIS);
                    writer.endElement();
                }
            }
            if (literal.length() > 0) {
                writer.startElement("a:literal");
                writer.addText(literal.toString());
                writer.endElement();
            }
        }
        writer.endElement();
    }

    protected static class StackFrame {
        public final HashMap<Symbol,Integer> childCounts;
        public final Symbol symbol;
        public final Map<String,String> attributes;
        public final int leftExtent;
        public final int rightExtent;
        public StackFrame(Symbol symbol, Map<String,String> attributes, int left, int right) {
            childCounts = new HashMap<>();
            this.symbol = symbol;
            this.attributes = attributes;
            this.leftExtent = left;
            this.rightExtent = right;
        }
    }

    private class TreeWriter {
        private final String DESCRIBE_AMBIGUITY = "https://nineml.org/ns/describe-ambiguity";
        private final XdmDestination destination;
        private final Receiver receiver;
        private final NamespaceMap nsmap;
        private boolean inDocument = false;
        private boolean seenRoot = false;

        public TreeWriter() throws CoffeeSacksException {
            Controller controller = new Controller(processor.getUnderlyingConfiguration());
            destination = new XdmDestination();
            PipelineConfiguration pipe = controller.makePipelineConfiguration();
            receiver = new ComplexContentOutputter(new NamespaceReducer(destination.getReceiver(pipe, new SerializationProperties())));
            receiver.setPipelineConfiguration(pipe);

            nsmap = NamespaceUtils.addToMap(NamespaceMap.emptyMap(), "a", DESCRIBE_AMBIGUITY);
        }

        public void startDocument() throws CoffeeSacksException {
            inDocument = true;
            seenRoot = false;
            try {
                receiver.setSystemId("https://nineml.org/");
                receiver.open();
                receiver.startDocument(0);
            } catch (XPathException err) {
                throw new CoffeeSacksException(CoffeeSacksException.ERR_TREE_CONSTRUCTION, "Internal error: ambiguous alternatives tree construction failed", err);
            }
        }

        public XdmNode endDocument() throws CoffeeSacksException {
            try {
                receiver.endDocument();
                receiver.close();
                inDocument = false;
                seenRoot = false;
                return destination.getXdmNode();
            } catch (XPathException err) {
                throw new CoffeeSacksException(CoffeeSacksException.ERR_TREE_CONSTRUCTION, "Internal error: ambiguous alternatives tree construction failed", err);
            }
        }

        private void startSymbol(Symbol symbol, HashMap<String,XdmAtomicValue> attributes) throws CoffeeSacksException {
            startSymbol(symbol, attributes, true);
        }

        private void startSymbol(Symbol symbol, HashMap<String,XdmAtomicValue> attributes, boolean showMark) throws CoffeeSacksException {
            HashMap<String,XdmAtomicValue> localAttr = new HashMap<>(attributes);
            if (showMark && symbol.getAttribute("mark") != null) {
                localAttr.put("mark", new XdmAtomicValue(symbol.getAttribute("mark").getValue()));
            }
            if (symbol instanceof NonterminalSymbol) {
                NonterminalSymbol nt = (NonterminalSymbol) symbol;
                final String name;
                if (nt.getName().startsWith("$")) {
                    name = "a:nonterminal";
                } else {
                    name = nt.getName();
                }
                localAttr.put("name", new XdmAtomicValue(nt.getName()));
                startElement(name, localAttr);
            } else if (symbol instanceof TerminalSymbol) {
                if (symbol.getAttribute("tmark") != null) {
                    localAttr.put("mark", new XdmAtomicValue(symbol.getAttribute("tmark").getValue()));
                }
                TerminalSymbol ts = (TerminalSymbol) symbol;
                startElement("a:literal", localAttr);
                addText(ts.getToken().getValue());
                endElement();
            } else {
                throw new CoffeeSacksException(CoffeeSacksException.ERR_TREE_CONSTRUCTION, "Internal error: unexpected symbol class constructing ambiguous alternatives");
            }
        }

        public void addLiteral(StringBuilder text, String mark) throws CoffeeSacksException {
            if (text.length() > 0) {
                HashMap<String,XdmAtomicValue> attributes = new HashMap<>();
                attributes.put("mark", new XdmAtomicValue(mark));
                startElement("a:literal", attributes);
                addText(text.toString());
                endElement();
                text.setLength(0);
            }
        }

        public void startElement(String name) throws CoffeeSacksException {
            startElement(name, new HashMap<>());
        }

        public void startElement(String name, HashMap<String,XdmAtomicValue> attributes) throws CoffeeSacksException {
            if (!inDocument) {
                throw new CoffeeSacksException(CoffeeSacksException.ERR_TREE_CONSTRUCTION, "Internal error: not in document constructing ambiguous alternatives");
            }
            try {
                final FingerprintedQName nodeName;
                if (name.startsWith("a:")) {
                    nodeName = NamespaceUtils.fqName("a", DESCRIBE_AMBIGUITY, name.substring(2));
                } else {
                    nodeName = NamespaceUtils.fqName("", "", name);
                }

                AttributeMap map = EmptyAttributeMap.getInstance();
                if (!seenRoot) {
                    map = map.put(new AttributeInfo(NamespaceUtils.fqName("a", DESCRIBE_AMBIGUITY, "version"), BuiltInAtomicType.UNTYPED_ATOMIC, "1.0", VoidLocation.getInstance(), 0));
                }
                for (String key : attributes.keySet()) {
                    XdmAtomicValue value = attributes.get(key);
                    FingerprintedQName attrName = NamespaceUtils.fqName("", "", key);
                    map = map.put(new AttributeInfo(attrName, value.getUnderlyingValue().getPrimitiveType(), value.getStringValue(), VoidLocation.getInstance(), 0));
                }

                seenRoot = true;
                receiver.startElement(nodeName, Untyped.INSTANCE, map, nsmap, VoidLocation.getInstance(), 0);
            } catch (XPathException err) {
                throw new CoffeeSacksException(CoffeeSacksException.ERR_TREE_CONSTRUCTION, "Internal error: ambiguous alternatives tree construction failed", err);
            }
        }

        public void endElement() throws CoffeeSacksException {
            if (!inDocument) {
                throw new CoffeeSacksException(CoffeeSacksException.ERR_TREE_CONSTRUCTION, "Internal error: not in document constructing ambiguous alternatives");
            }
            try {
                receiver.endElement();
            } catch (XPathException err) {
                throw new CoffeeSacksException(CoffeeSacksException.ERR_TREE_CONSTRUCTION, "Internal error: ambiguous alternatives tree construction failed", err);
            }
        }

        public void addText(String text) throws CoffeeSacksException {
            try {
                receiver.characters(StringView.of(text), VoidLocation.getInstance(), 0);
            } catch (XPathException err) {
                throw new CoffeeSacksException(CoffeeSacksException.ERR_TREE_CONSTRUCTION, "Internal error: ambiguous alternatives tree construction failed", err);
            }
        }
    }

    protected static class VoidLocation implements Location {
        private static final VoidLocation INSTANCE = new VoidLocation();
        private VoidLocation() {
            // nop
        }
        public static VoidLocation getInstance() {
            return INSTANCE;
        }

        @Override
        public String getSystemId() {
            return null;
        }

        @Override
        public String getPublicId() {
            return null;
        }

        @Override
        public int getLineNumber() {
            return -1;
        }

        @Override
        public int getColumnNumber() {
            return -1;
        }

        @Override
        public Location saveLocation() {
            return this;
        }
    }
}
