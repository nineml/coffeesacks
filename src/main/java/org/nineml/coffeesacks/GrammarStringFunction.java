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
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceType;
import org.nineml.coffeefilter.InvisibleXmlDocument;
import org.nineml.coffeefilter.InvisibleXmlParser;
import org.xml.sax.InputSource;

import javax.xml.transform.sax.SAXSource;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/**
 * A Saxon extension function load an Invisible XML grammar.
 * <p>Assuming the <code>cs:</code> prefix is bound to the CoffeeSacks namespace,
 * <code>cs:grammar(href [, options])</code> loads a grammar.
 * </p>
 */public class GrammarStringFunction extends CommonDefinition {
    private static final StructuredQName qName =
            new StructuredQName("", "http://nineml.com/ns/coffeesacks", "grammar-string");
    private URI baseURI = null;

    public GrammarStringFunction(Configuration config, ParserCache cache) {
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
            String grammarString = sequences[0].head().getStringValue();

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
                    if ("ixml".equals(grammarType)) {
                        parser = invisibleXml.getParserFromIxml(grammarString);
                    } else {
                        throw new IllegalArgumentException("Only ixml grammars can be parsed from strings: " + grammarType);
                    }
                } else {
                    parser = invisibleXml.getParserFromIxml(grammarString);
                }

                DocumentBuilder builder = processor.newDocumentBuilder();

                if (parser.constructed()) {
                    ByteArrayInputStream bais = new ByteArrayInputStream(parser.getCompiledParser().getBytes(StandardCharsets.UTF_8));
                    SAXSource source = new SAXSource(new InputSource(bais));
                    grammar = builder.build(source);
                } else {
                    InvisibleXmlDocument failed = parser.getFailedParse();
                    ByteArrayInputStream bais = new ByteArrayInputStream(failed.getTree().getBytes(StandardCharsets.UTF_8));
                    SAXSource source = new SAXSource(new InputSource(bais));
                    return builder.build(source).getUnderlyingNode();
                }

                return grammar.getUnderlyingNode();
            } catch (Exception ex) {
                throw new XPathException(ex);
            }
        }
    }
}
