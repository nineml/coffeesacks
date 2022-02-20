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
import org.xmlresolver.utils.URIUtils;

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
    protected static final QName _cache = new QName("", "cache");
    protected static final QName _encoding = new QName("", "encoding");
    protected static final QName _type = new QName("", "type");
    protected static final QName _format = new QName("", "format");
    protected final ParserOptions parserOptions = new ParserOptions();

    protected final Configuration config;
    protected final ParserCache cache;

    public CommonDefinition(Configuration config, ParserCache cache) {
        this.cache = cache;
        this.config = config;
        parserOptions.logger = new SacksLogger(config.getLogger());
    }

    protected HashMap<QName,String> parseMap(MapItem item) throws XPathException {
        HashMap<QName,String> options = new HashMap<>();
        for (KeyValuePair kv : item.keyValuePairs()) {
            QName key = null;
            if (kv.key.getItemType() == BuiltInAtomicType.QNAME) {
                QNameValue qkey = (QNameValue) kv.key;
                key = new QName(qkey.getPrefix(), qkey.getNamespaceURI(), qkey.getLocalName());
            } else {
                key = new QName("", "", kv.key.getStringValue());
            }
            String value = kv.value.getStringValue();
            options.put(key, value);
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

        HashMap<QName,String> options;
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

            InvisibleXmlParser parser;
            if (cache.nodeCache.containsKey(grammar)) {
                parser = cache.nodeCache.get(grammar);
            } else {
                // This really isn't very nice
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Serializer serializer = processor.newSerializer(baos);
                serializer.serialize(grammar);
                ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
                parser = InvisibleXml.getParser(bais, grammar.getBaseURI());

                if ("true".equals(options.getOrDefault(_cache, "true"))
                        || "yes".equals(options.getOrDefault(_cache, "yes"))) {
                    cache.nodeCache.put(grammar, parser);
                }
            }

            InvisibleXmlDocument document = parser.parse(source, encoding);

            if ("xml".equals(format)) {
                DocumentBuilder builder = processor.newDocumentBuilder();
                BuildingContentHandler handler = builder.newBuildingContentHandler();
                document.getTree(handler);
                return handler.getDocumentNode().getUnderlyingNode();
            }

            String json;
            ParserOptions newOptions = new ParserOptions(parserOptions);
            newOptions.assertValidXmlNames = false;
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

            // This also really isn't very nice
            XPathCompiler compiler = processor.newXPathCompiler();
            QName _json = new QName("", "json");
            compiler.declareVariable(_json);
            XPathExecutable exec = compiler.compile("parse-json($json)");
            XPathSelector selector = exec.load();
            selector.setVariable(_json, new XdmAtomicValue(json));
            XdmSequenceIterator<XdmItem> iter = selector.iterator();
            XdmItem item = iter.next();
            return item.getUnderlyingValue();
        } catch (Exception ex) {
            throw new XPathException(ex);
        }
    }
}
