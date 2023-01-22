package org.nineml.coffeesacks;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.AbstractFunction;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.value.AnyURIValue;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;
import org.nineml.coffeefilter.InvisibleXmlParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class MakeParser extends CommonDefinition {
    private static final StructuredQName qName =
            new StructuredQName("", "http://nineml.com/ns/coffeesacks", "make-parser");

    public MakeParser(Configuration config) {
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
        return new SequenceType[]{SequenceType.SINGLE_ITEM, SequenceType.OPTIONAL_ITEM};
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
            final InvisibleXmlParser parser;

            if (input instanceof AnyURIValue) {
                String grammarHref = ((AnyURIValue) input).getStringValue();
                final URI grammarURI;
                if (baseURI != null) {
                    grammarURI = baseURI.resolve(grammarHref);
                } else {
                    grammarURI = URIUtils.resolve(URIUtils.cwd(), grammarHref);
                }
                parser = parserFromURI(context, grammarURI, options);
            } else if (input instanceof AtomicValue) {
                parser = parserFromString(context, ((StringValue) input).getStringValue(), options);
            } else if (input instanceof NodeInfo) {
                parser = parserFromXml(context, (NodeInfo) input, options);
            } else if (input instanceof MapItem) {
                throw new CoffeeSacksException(CoffeeSacksException.ERR_INVALID_GRAMMAR, "Cannot parse a map as Invisible XML", sourceLoc, input);
            } else if (input instanceof ArrayItem) {
                throw new CoffeeSacksException(CoffeeSacksException.ERR_INVALID_GRAMMAR, "Cannot parse an array as Invisible XML", sourceLoc, input);
            } else if (input instanceof AbstractFunction) {
                throw new CoffeeSacksException(CoffeeSacksException.ERR_INVALID_GRAMMAR, "Cannot parse a function item as Invisible XML", sourceLoc, input);
            } else {
                throw new CoffeeSacksException(CoffeeSacksException.ERR_INVALID_GRAMMAR, "Cannot parse argument as Invisible XML", sourceLoc, input);
            }

            return functionFromParser(context, parser, options);
        }
    }
}
