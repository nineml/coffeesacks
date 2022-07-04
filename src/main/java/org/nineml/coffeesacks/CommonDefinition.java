package org.nineml.coffeesacks;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.s9api.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.value.AtomicValue;
import org.nineml.coffeefilter.InvisibleXml;
import org.nineml.coffeefilter.InvisibleXmlDocument;
import org.nineml.coffeefilter.InvisibleXmlParser;
import org.nineml.coffeefilter.ParserOptions;
import org.nineml.coffeefilter.trees.DataTree;
import org.nineml.coffeefilter.trees.DataTreeBuilder;
import org.nineml.coffeefilter.trees.SimpleTree;
import org.nineml.coffeefilter.trees.SimpleTreeBuilder;
import org.nineml.coffeegrinder.parser.HygieneReport;
import org.xml.sax.InputSource;

import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/**
 * Superclass for the CoffeeSacks functions containing some common definitions.
 */
public abstract class CommonDefinition extends ExtensionFunctionDefinition {
    public static final String logcategory = "CoffeeSacks";
    public static final QName cxml = new QName("", "http://nineml.org/coffeegrinder/ns/grammar/compiled", "grammar");
    public static final QName ixml_state = new QName("ixml", "http://invisiblexml.org/NS", "state");
    public static final String cs_namespace = "https://ninenl.org/ns/coffeesacks/error";

    protected static final String _cache = "cache";
    protected static final String _encoding = "encoding";
    protected static final String _type = "type";
    protected static final String _format = "format";

    private static final QName _json = new QName("", "json");

    protected static ParserOptions parserOptions = null;
    protected static InvisibleXml invisibleXml = null;
    protected final Configuration config;
    protected final ParserCache cache;

    public CommonDefinition(Configuration config, ParserCache cache) {
        if (parserOptions == null) {
            parserOptions = new ParserOptions();
            parserOptions.setLogger(new SacksLogger(config.getLogger()));
        }

        if (invisibleXml == null) {
            invisibleXml = new InvisibleXml(parserOptions);
        }

        this.cache = cache;
        this.config = config;
    }

    protected HashMap<String,String> parseMap(MapItem item) throws XPathException {
        HashMap<String,String> options = new HashMap<>();

        // The implementation of the keyValuePairs() method is incompatible between Saxon 10 and Saxon 11.
        // In order to avoid having to publish two versions of this class, we use reflection to
        // work it out at runtime. (Insert programmer barfing on his shoes emoji here.)
        try {
            Method keys = MapItem.class.getMethod("keys");
            Method get = MapItem.class.getMethod("get", AtomicValue.class);
            AtomicIterator aiter = (AtomicIterator) keys.invoke(item);
            AtomicValue next = aiter.next();
            while (next != null) {
                AtomicValue value = (AtomicValue) get.invoke(item, next);
                options.put(next.getStringValue(), value.getStringValue());
                next = aiter.next();
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalArgumentException("Failed to resolve MapItem with reflection");
        }

        return options;
    }

    protected Sequence processInvisibleXmlURI(URI baseURI, XPathContext context, Sequence[] sequences) throws XPathException, IOException {
        String inputHref = sequences[1].head().getStringValue();

        URI inputURI;
        if (baseURI != null) {
            inputURI = baseURI.resolve(inputHref);
        } else {
            inputURI = URIUtils.resolve(URIUtils.cwd(), inputHref);
        }

        URLConnection conn = inputURI.toURL().openConnection();
        return processInvisibleXml(context, sequences, conn.getInputStream());
    }

    protected Sequence processInvisibleXmlString(XPathContext context, Sequence[] sequences) throws XPathException {
        String input = sequences[1].head().getStringValue();
        // Hack to make the input available as a stream
        ByteArrayInputStream stream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        return processInvisibleXml(context, sequences, stream);
    }

    protected Sequence processInvisibleXml(XPathContext context, Sequence[] sequences, InputStream source) throws XPathException {
        NodeInfo grammar = (NodeInfo) sequences[0].head();

        HashMap<String,String> options;
        if (sequences.length > 2) {
            Item item = sequences[2].head();
            if (item instanceof MapItem) {
                options = parseMap((MapItem) item);
            } else {
                throw new XPathException("Third argument CoffeeSacks parse function must be a map");
            }
        } else {
            options = new HashMap<>();
        }

        String format = options.getOrDefault(_format, "xml");
        if (!"xml".equals(format)
                && !"json".equals(format) && !"json-data".equals(format)
                && !"json-tree".equals(format) && !"json-text".equals(format)) {
            throw new XPathException("Unexpected format requested: " + format);
        }

        XdmNode node = new XdmNode(grammar);
        if (node.getNodeKind() == XdmNodeKind.DOCUMENT) {
            XdmSequenceIterator<XdmNode> iter = node.axisIterator(Axis.CHILD);
            while (iter.hasNext()) {
                XdmNode child = iter.next();
                if (child.getNodeKind() == XdmNodeKind.ELEMENT) {
                    node = child;
                    break;
                }
            }
        }

        if (node.getNodeKind() == XdmNodeKind.ELEMENT) {
            String state = node.getAttributeValue(ixml_state);
            if (state != null && state.contains("fail")) {
                return earlyFailBadGrammar(node, format);
            }
            if (!cxml.equals(node.getNodeName())) {
                return earlyFailNotGrammar(node, format);
            }
        } else {
            return earlyFailNotXml(node, format);
        }

        try {
            // Can this ever fail?
            Processor processor = (Processor) context.getConfiguration().getProcessor();

            String encoding = options.getOrDefault(_encoding, "UTF-8");

            InvisibleXmlParser parser = getParserForGrammar(processor, options, grammar);

            InvisibleXmlDocument document = parser.parse(source, encoding);

            if ("xml".equals(format)) {
                DocumentBuilder builder = processor.newDocumentBuilder();
                BuildingContentHandler handler = builder.newBuildingContentHandler();
                document.getTree(handler);
                return handler.getDocumentNode().getUnderlyingNode();
            }

            String json;
            ParserOptions newOptions = new ParserOptions();
            newOptions.setAssertValidXmlNames(false);
            if ("json-tree".equals(format) || "json-text".equals(format)) {
                SimpleTreeBuilder builder = new SimpleTreeBuilder(newOptions);
                document.getTree(builder);
                SimpleTree tree = builder.getTree();
                json = tree.asJSON();
            } else {
                DataTreeBuilder builder = new DataTreeBuilder(newOptions);
                document.getTree(builder);
                DataTree tree = builder.getTree();
                json = tree.asJSON();
            }

            return jsonToXDM(processor, json);
        } catch (Exception ex) {
            throw new XPathException(ex);
        }
    }

    private Sequence earlyFailBadGrammar(XdmNode node, String format) {
        if ("json".equals(format)) {
            XdmMap map = new XdmMap();
            map = map.put(new XdmAtomicValue("cs:error"), new XdmAtomicValue("badgrammar"));
            map = map.put(new XdmAtomicValue("ixml:state"), new XdmAtomicValue("fail"));
            map = map.put(new XdmAtomicValue("grammar"), node);
            return map.getUnderlyingValue();
        }

        XmlXdmWriter writer = new XmlXdmWriter(node.getProcessor());
        writer.startDocument();
        writer.declareNamespace("ixml", ixml_state.getNamespaceURI());
        writer.declareNamespace("cs", cs_namespace);
        writer.startElement("cs:error");
        writer.addAttribute("ixml:state", "fail");
        writer.addAttribute("code", "badgrammar");
        writer.addNode(node);
        writer.endElement();
        writer.endDocument();
        return writer.getDocument().getUnderlyingNode();
    }

    private Sequence earlyFailNotGrammar(XdmNode node, String format) {
        if ("json".equals(format)) {
            XdmMap map = new XdmMap();
            map = map.put(new XdmAtomicValue("cs:error"), new XdmAtomicValue("notgrammar"));
            map = map.put(new XdmAtomicValue("ixml:state"), new XdmAtomicValue("fail"));
            map = map.put(new XdmAtomicValue("grammar"), node);
            return map.getUnderlyingValue();
        }

        XmlXdmWriter writer = new XmlXdmWriter(node.getProcessor());
        writer.startDocument();
        writer.declareNamespace("ixml", ixml_state.getNamespaceURI());
        writer.declareNamespace("cs", cs_namespace);
        writer.startElement("cs:error");
        writer.addAttribute("ixml:state", "fail");
        writer.addAttribute("code", "notgrammar");
        writer.addNode(node);
        writer.endElement();
        writer.endDocument();
        return writer.getDocument().getUnderlyingNode();
    }

    private Sequence earlyFailNotXml(XdmNode node, String format) {
        if ("json".equals(format)) {
            XdmMap map = new XdmMap();
            map = map.put(new XdmAtomicValue("cs:error"), new XdmAtomicValue("notxml"));
            map = map.put(new XdmAtomicValue("ixml:state"), new XdmAtomicValue("fail"));
            map = map.put(new XdmAtomicValue("grammar"), node);
            return map.getUnderlyingValue();
        }

        XmlXdmWriter writer = new XmlXdmWriter(node.getProcessor());
        writer.startDocument();
        writer.declareNamespace("ixml", ixml_state.getNamespaceURI());
        writer.declareNamespace("cs", cs_namespace);
        writer.startElement("cs:error");
        writer.addAttribute("ixml:state", "fail");
        writer.addAttribute("code", "notxml");
        writer.addNode(node);
        writer.endElement();
        writer.endDocument();
        return writer.getDocument().getUnderlyingNode();
    }

    protected Item jsonToXDM(Processor processor, String json) throws SaxonApiException {
        // This also really isn't very nice
        XPathCompiler compiler = processor.newXPathCompiler();
        compiler.declareVariable(_json);
        XPathExecutable exec = compiler.compile("parse-json($json)");
        XPathSelector selector = exec.load();
        selector.setVariable(_json, new XdmAtomicValue(json));
        XdmSequenceIterator<XdmItem> iter = selector.iterator();
        XdmItem item = iter.next();
        return item.getUnderlyingValue();
    }

    protected InvisibleXmlParser getParserForGrammar(Processor processor, HashMap<String,String> options, NodeInfo grammar) throws IOException, SaxonApiException {
        InvisibleXmlParser parser;
        if (cache.nodeCache.containsKey(grammar)) {
            parser = cache.nodeCache.get(grammar);
        } else {
            // This really isn't very nice
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Serializer serializer = processor.newSerializer(baos);
            serializer.serialize(grammar);
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            parser = invisibleXml.getParser(bais, grammar.getBaseURI());

            if ("true".equals(options.getOrDefault(_cache, "true"))
                    || "yes".equals(options.getOrDefault(_cache, "yes"))) {
                cache.nodeCache.put(grammar, parser);
            }
        }

        return parser;
    }
}
