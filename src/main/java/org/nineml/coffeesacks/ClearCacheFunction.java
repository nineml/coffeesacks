package org.nineml.coffeesacks;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.SequenceType;

/**
 * A Saxon extension function to clear the grammar cache.
 *
 * <p>Assuming the <code>cs:</code> prefix is bound to the CoffeeSacks namespace,
 * <code>cs:clear-cache()</code> clears the cache.
 * </p>
 */
public class ClearCacheFunction extends ExtensionFunctionDefinition {
    private static final StructuredQName qName =
            new StructuredQName("", "http://nineml.com/ns/coffeesacks", "clear-cache");

    private final ParserCache cache;

    public ClearCacheFunction(ParserCache cache) {
        this.cache = cache;
    }

    @Override
    public StructuredQName getFunctionQName() {
        return qName;
    }


    @Override
    public int getMinimumNumberOfArguments() {
        return 0;
    }

    @Override
    public int getMaximumNumberOfArguments() {
        return 0;
    }

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[0];
    }

    @Override
    public SequenceType getResultType(SequenceType[] sequenceTypes) {
        return SequenceType.SINGLE_BOOLEAN;
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new FlushCall();
    }

    private class FlushCall extends ExtensionFunctionCall {
        @Override
        public Sequence call(XPathContext xPathContext, Sequence[] sequences) throws XPathException {
            cache.clear();
            return BooleanValue.TRUE;
        }
    }
}
