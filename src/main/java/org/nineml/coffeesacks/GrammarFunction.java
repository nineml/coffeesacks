package org.nineml.coffeesacks;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceType;
import org.nineml.coffeefilter.InvisibleXml;
import org.nineml.coffeefilter.InvisibleXmlDocument;
import org.nineml.coffeefilter.InvisibleXmlParser;
import org.nineml.coffeesacks.utils.ParseUtils;

import java.io.File;
import java.util.HashMap;

public class GrammarFunction extends ExtensionFunctionDefinition {
    private static final QName _type = new QName("", "type");
    private static final StructuredQName qName =
            new StructuredQName("", "http://nineml.com/ns/coffeesacks", "grammar");

    public GrammarFunction(ParserCache cache) {

    }

    @Override
    public StructuredQName getFunctionQName() {
        return qName;
    }

    @Override
    public int getMinimumNumberOfArguments() {
        return 1;
    }

    @Override
    public int getMaximumNumberOfArguments() {
        return 2;
    }

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[]{SequenceType.SINGLE_STRING, SequenceType.OPTIONAL_NODE};
    }

    @Override
    public SequenceType getResultType(SequenceType[] sequenceTypes) {
        return SequenceType.SINGLE_ITEM;
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new GrammarCall();
    }

    private static class GrammarCall extends ExtensionFunctionCall {
        @Override
        public Sequence call(XPathContext xPathContext, Sequence[] sequences) throws XPathException {
            String grammarHref = sequences[0].head().getStringValue();
            HashMap<QName,String> options;
            if (sequences.length > 1) {
                Item item = sequences[1].head();
                if (item instanceof MapItem) {
                    options = ParseUtils.parseMap((MapItem) item);
                } else {
                    throw new XPathException("Second argument to cs:grammar must be a map");
                }
            } else {
                options = new HashMap<>();
            }

            try {
                // Can this ever fail?
                Processor processor = (Processor) xPathContext.getConfiguration().getProcessor();
                XdmNode grammar = null;
                String grammarType = options.getOrDefault(_type, "ixml");

                if ("ixml".equals(grammarType)) {
                    InvisibleXmlParser parser = InvisibleXml.invisibleXmlParser();
                    InvisibleXmlDocument document = parser.parseFromFile(grammarHref);

                    DocumentBuilder builder = processor.newDocumentBuilder();
                    BuildingContentHandler handler = builder.newBuildingContentHandler();
                    document.getTree(handler);
                    grammar = handler.getDocumentNode();
                } else if ("xml".equals(grammarType) || "vxml".equals(grammarType)) {
                    DocumentBuilder builder = processor.newDocumentBuilder();
                    grammar = builder.build(new File(grammarHref));
                } else if ("compiled".equals(grammarType) || "cxml".equals(grammarType)) {
                    throw new UnsupportedOperationException("Loading compiled grammars is not supported yet");
                } else {
                    throw new XPathException("Unexpected grammar type: " + grammarType);
                }
                return grammar.getUnderlyingNode();
            } catch (Exception ex) {
                throw new XPathException(ex);
            }
        }

    }
}
