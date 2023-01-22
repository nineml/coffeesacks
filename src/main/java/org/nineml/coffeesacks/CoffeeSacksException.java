package org.nineml.coffeesacks;

import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.trans.XPathException;

public class CoffeeSacksException extends XPathException {
    public static final String COFFEE_SACKS_ERROR_PREFIX = "cse";
    public static final String COFFEE_SACKS_ERROR_NAMESPACE = "http://nineml.com/ns/coffeesacks/errors";

    public static final String ERR_HTTP_INF_LOOP = "CSHT0001";
    public static final String ERR_HTTP_LOOP = "CSHT0002";
    public static final String ERR_INVALID_GRAMMAR = "CSIX0001";
    public static final String ERR_BAD_GRAMMAR = "CSER0001";
    public static final String ERR_BAD_OPTIONS = "CSER0002";
    public static final String ERR_BAD_INPUT_FORMAT = "CSER0003";
    public static final String ERR_BAD_OUTPUT_FORMAT = "CSER0004";

    private final String message;

    public CoffeeSacksException(String errCode, String message, Location location) {
        this(errCode, message, location, null);
    }

    public CoffeeSacksException(String errCode, String message, Location location, Sequence value) {
        super(message);
        this.message = message;
        setErrorCodeQName(new StructuredQName(COFFEE_SACKS_ERROR_PREFIX, COFFEE_SACKS_ERROR_NAMESPACE, errCode));
        if (value != null) {
            setErrorObject(value);
        }
        setLocation(location);
    }

    @Override
    public String getMessage() {
        return message;
    }

}
