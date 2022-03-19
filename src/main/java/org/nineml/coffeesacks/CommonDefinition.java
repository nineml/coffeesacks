package org.nineml.coffeesacks;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.ma.map.KeyValuePair;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.s9api.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.QNameValue;
import org.nineml.coffeefilter.InvisibleXml;
import org.nineml.coffeefilter.InvisibleXmlDocument;
import org.nineml.coffeefilter.InvisibleXmlParser;
import org.nineml.coffeefilter.ParserOptions;
import org.nineml.coffeefilter.trees.DataTree;
import org.nineml.coffeefilter.trees.DataTreeBuilder;
import org.nineml.coffeefilter.trees.SimpleTree;
import org.nineml.coffeefilter.trees.SimpleTreeBuilder;
import org.nineml.coffeegrinder.parser.HygieneReport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/**
 * Superclass for the CoffeeSacks functions containing some common definitions.
 */
public abstract class CommonDefinition extends ExtensionFunctionDefinition {
    public static final String logcategory = "CoffeeSacks";

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
        for (KeyValuePair kv : item.keyValuePairs()) {
            options.put(kv.key.getStringValue(), kv.value.getStringValue());
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

        try {
            // Can this ever fail?
            Processor processor = (Processor) context.getConfiguration().getProcessor();

            String encoding = options.getOrDefault(_encoding, "UTF-8");

            InvisibleXmlParser parser = getParserForGrammar(processor, options, grammar);

            HygieneReport report = parser.getHygieneReport();
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
