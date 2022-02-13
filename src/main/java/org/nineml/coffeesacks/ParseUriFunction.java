package org.nineml.coffeesacks;

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
import org.nineml.coffeesacks.utils.ParseUtils;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.HashMap;

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
        @Override
        public Sequence call(XPathContext xpathContext, Sequence[] sequences) throws XPathException {
            NodeInfo grammar = (NodeInfo) sequences[0].head();
            String inputHref = sequences[1].head().getStringValue();
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
                // FIXME: what's the current base URI?
                URI inputURI = ParseUtils.resolve(ParseUtils.cwd(), inputHref);

                // Can this ever fail?
                Processor processor = (Processor) xpathContext.getConfiguration().getProcessor();

                InvisibleXmlParser parser;
                if (cache.nodeCache.containsKey(grammar)) {
                    parser = cache.nodeCache.get(grammar);
                } else {
                    URLConnection conn = inputURI.toURL().openConnection();

                    // This really isn't very nice
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    Serializer serializer = processor.newSerializer(baos);
                    serializer.serialize(grammar);
                    parser = InvisibleXml.parserFromVxmlString(baos.toString());
                    if ("true".equals(options.getOrDefault(_cache, "true"))) {
                        cache.nodeCache.put(grammar, parser);
                    }
                }

                String encoding = options.getOrDefault(_encoding, "UTF-8");

                URLConnection conn = inputURI.toURL().openConnection();
                InvisibleXmlDocument document = parser.parseFromStream(conn.getInputStream(), encoding);
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
