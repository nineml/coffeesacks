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
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AnyURIValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;
import org.nineml.coffeefilter.InvisibleXmlParser;

import java.net.URI;
import java.util.HashMap;

public class LoadGrammar extends CommonDefinition {
    private static final StructuredQName qName =
            new StructuredQName("", "http://nineml.com/ns/coffeesacks", "load-grammar");

    public LoadGrammar(Configuration config) {
        super(config);
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
        return new SequenceType[]{SequenceType.SINGLE_ATOMIC, SequenceType.OPTIONAL_ITEM};
    }

    @Override
    public SequenceType getResultType(SequenceType[] sequenceTypes) {
        return SequenceType.SINGLE_FUNCTION;
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new FunctionCall();
    }

    private class FunctionCall extends ExtensionFunctionCall {
        @Override
        public void supplyStaticContext(StaticContext context, int locationId, Expression[] arguments) throws XPathException {
            sourceLoc = context.getContainingLocation();
            if (context.getStaticBaseURI() != null && !"".equals(context.getStaticBaseURI())) {
                baseURI = URIUtils.resolve(URIUtils.cwd(), context.getStaticBaseURI());
            }
        }

        @Override
        public Sequence call(XPathContext context, Sequence[] sequences) throws XPathException {
            HashMap<String, String> options;
            if (sequences.length > 1) {
                Item item = sequences[1].head();
                if (item instanceof MapItem) {
                    options = parseMap((MapItem) item);
                    checkOptions(options);
                } else {
                    throw new CoffeeSacksException(CoffeeSacksException.ERR_BAD_OPTIONS, "Options must be a map", sourceLoc);
                }
            } else {
                options = new HashMap<>();
            }

            Sequence input = sequences[0].head();
            final String grammarHref;
            if (input instanceof AnyURIValue) {
                grammarHref = ((AnyURIValue) input).getStringValue();
            } else if (input instanceof StringValue) {
                grammarHref = ((StringValue) input).getStringValue();
            } else {
                throw new CoffeeSacksException(CoffeeSacksException.ERR_BAD_GRAMMAR, "Grammar must be a string or a URI", sourceLoc);
            }

            final InvisibleXmlParser parser;
            final URI grammarURI;
            if (baseURI != null) {
                grammarURI = baseURI.resolve(grammarHref);
            } else {
                grammarURI = URIUtils.resolve(URIUtils.cwd(), grammarHref);
            }
            parser = parserFromURI(context, grammarURI, options);
            return functionFromParser(context, parser, options);
        }
    }
}
