package org.nineml.coffeesacks;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceType;
import org.nineml.coffeefilter.InvisibleXmlDocument;
import org.nineml.coffeefilter.InvisibleXmlParser;
import org.xml.sax.InputSource;

import javax.xml.transform.sax.SAXSource;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/**
 * A Saxon extension function load an Invisible XML grammar.
 * <p>Assuming the <code>cs:</code> prefix is bound to the CoffeeSacks namespace,
 * <code>cs:grammar(href [, options])</code> loads a grammar.
 * </p>
 */public class GrammarUriFunction extends CommonDefinition {
    private static final StructuredQName qName =
            new StructuredQName("", "http://nineml.com/ns/coffeesacks", "grammar-uri");
    private URI baseURI = null;

    public GrammarUriFunction(Configuration config, ParserCache cache) {
        super(config, cache);
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
        return new SequenceType[]{SequenceType.SINGLE_STRING, SequenceType.OPTIONAL_ITEM};
    }

    @Override
    public SequenceType getResultType(SequenceType[] sequenceTypes) {
        return SequenceType.SINGLE_ITEM;
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new GrammarCall();
    }

    private class GrammarCall extends ExtensionFunctionCall {
        @Override
        public void supplyStaticContext(StaticContext context, int locationId, Expression[] arguments) throws XPathException {
            if (context.getStaticBaseURI() != null && !"".equals(context.getStaticBaseURI())) {
                baseURI = URIUtils.resolve(URIUtils.cwd(), context.getStaticBaseURI());
            }
        }

        @Override
        public Sequence call(XPathContext xPathContext, Sequence[] sequences) throws XPathException {
            String grammarHref = sequences[0].head().getStringValue();
            URI grammarURI;
            if (baseURI != null) {
                grammarURI = baseURI.resolve(grammarHref);
            } else {
                grammarURI = URIUtils.resolve(URIUtils.cwd(), grammarHref);
            }

            if (cache.uriCache.containsKey(grammarURI)) {
                return cache.uriCache.get(grammarURI);
            }

            HashMap<String,String> options;
            if (sequences.length > 1) {
                Item item = sequences[1].head();
                if (item instanceof MapItem) {
                    options = parseMap((MapItem) item);
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

                InvisibleXmlParser parser = null;
                if (options.containsKey(_type)) {
                    String grammarType = options.get(_type);
                    URLConnection conn = grammarURI.toURL().openConnection();
                    if ("ixml".equals(grammarType)) {
                        String encoding = options.getOrDefault(_encoding, "UTF-8");
                        parser = invisibleXml.getParserFromIxml(conn.getInputStream(), encoding);
                    } else if ("xml".equals(grammarType) || "vxml".equals(grammarType)) {
                        parser = invisibleXml.getParserFromVxml(conn.getInputStream(), grammarURI.toString());
                    } else if ("cxml".equals(grammarType) || "compiled".equals(grammarType)) {
                        parser = invisibleXml.getParserFromCxml(conn.getInputStream(), grammarURI.toString());
                    } else {
                        throw new IllegalArgumentException("Unexpected grammar type: " + grammarType);
                    }
                } else {
                    parser = invisibleXml.getParser(grammarURI);
                }

                DocumentBuilder builder = processor.newDocumentBuilder();

                if (parser.constructed()) {
                    ByteArrayInputStream bais = new ByteArrayInputStream(parser.getCompiledParser().getBytes(StandardCharsets.UTF_8));
                    SAXSource source = new SAXSource(new InputSource(bais));
                    grammar = builder.build(source);
                    if ("true".equals(options.getOrDefault(_cache, "true"))
                            || "yes".equals(options.getOrDefault(_cache, "yes"))) {
                        cache.uriCache.put(grammarURI, grammar.getUnderlyingNode());
                    }

                    return grammar.getUnderlyingNode();
                } else {
                    InvisibleXmlDocument failed = parser.getFailedParse();
                    ByteArrayInputStream bais = new ByteArrayInputStream(failed.getTree().getBytes(StandardCharsets.UTF_8));
                    SAXSource source = new SAXSource(new InputSource(bais));
                    return builder.build(source).getUnderlyingNode();
                }
            } catch (Exception ex) {
                throw new XPathException(ex);
            }
        }
    }
}
