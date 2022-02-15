package org.nineml.coffeesacks;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceType;
import org.nineml.coffeefilter.InvisibleXml;
import org.nineml.coffeefilter.InvisibleXmlDocument;
import org.nineml.coffeefilter.InvisibleXmlParser;
import org.nineml.coffeefilter.utils.URIUtils;
import org.nineml.coffeesacks.utils.ParseUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.HashMap;

/**
 * A Saxon extension function parse a document against an Invisible XML grammar.
 *
 * <p>Assuming the <code>cs:</code> prefix is bound to the CoffeeSacks namespace,
 * <code>cs:parse-uri(grammar, href, [, options])</code> loads the document identified
 * by <code>href</code>, parses it against the grammar, and returns the result.
 * </p>
 */
public class ParseUriFunction extends ExtensionFunctionDefinition {
    private static final QName _cache = new QName("", "cache");
    private static final QName _encoding = new QName("", "encoding");

    private static final StructuredQName qName =
            new StructuredQName("", "http://nineml.com/ns/coffeesacks", "parse-uri");

    private final ParserCache cache;

    public ParseUriFunction(ParserCache cache) {
        this.cache = cache;
    }

    @Override
    public StructuredQName getFunctionQName() {
        return qName;
    }

    @Override
    public int getMinimumNumberOfArguments() {
        return 2;
    }

    @Override
    public int getMaximumNumberOfArguments() {
        return 3;
    }

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[]{
                SequenceType.SINGLE_NODE,
                SequenceType.SINGLE_ATOMIC,
                SequenceType.OPTIONAL_ITEM};
    }

    @Override
    public SequenceType getResultType(SequenceType[] sequenceTypes) {
        return SequenceType.SINGLE_NODE;
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new ParseUriCall();
    }

    private class ParseUriCall extends ExtensionFunctionCall {
        private URI baseURI = null;

        @Override
        public void supplyStaticContext(StaticContext context, int locationId, Expression[] arguments) throws XPathException {
            if (context.getStaticBaseURI() != null && !"".equals(context.getStaticBaseURI())) {
                baseURI = org.xmlresolver.utils.URIUtils.resolve(org.xmlresolver.utils.URIUtils.cwd(), context.getStaticBaseURI());
            }
        }
        @Override
        public Sequence call(XPathContext xpathContext, Sequence[] sequences) throws XPathException {
            NodeInfo grammar = (NodeInfo) sequences[0].head();
            String inputHref = sequences[1].head().getStringValue();
            URI inputURI;
            if (baseURI != null) {
                inputURI = baseURI.resolve(inputHref);
            } else {
                inputURI = org.xmlresolver.utils.URIUtils.resolve(org.xmlresolver.utils.URIUtils.cwd(), inputHref);
            }

            HashMap<QName,String> options;
            if (sequences.length > 2) {
                Item item = sequences[2].head();
                if (item instanceof MapItem) {
                    options = ParseUtils.parseMap((MapItem) item);
                } else {
                    throw new XPathException("Third argument to cs:parse must be a map");
                }
            } else {
                options = new HashMap<>();
            }

            try {
                // Can this ever fail?
                Processor processor = (Processor) xpathContext.getConfiguration().getProcessor();

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

                URLConnection conn = inputURI.toURL().openConnection();
                InvisibleXmlDocument document = parser.parse(conn.getInputStream(), encoding);
                DocumentBuilder builder = processor.newDocumentBuilder();
                BuildingContentHandler handler = builder.newBuildingContentHandler();
                document.getTree(handler);
                return handler.getDocumentNode().getUnderlyingNode();
            } catch (Exception ex) {
                throw new XPathException(ex);
            }
        }
    }
}
