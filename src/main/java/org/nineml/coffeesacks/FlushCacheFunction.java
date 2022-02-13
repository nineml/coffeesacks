package org.nineml.coffeesacks;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.SequenceType;

public class FlushCacheFunction extends ExtensionFunctionDefinition {
    private static final StructuredQName qName =
            new StructuredQName("", "http://nineml.com/ns/coffeesacks", "flush-cache");

    private final ParserCache cache;

    public FlushCacheFunction(ParserCache cache) {
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
            cache.flush();
            return BooleanValue.TRUE;
        }
    }
}
