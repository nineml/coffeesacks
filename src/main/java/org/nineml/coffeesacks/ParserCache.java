package org.nineml.coffeesacks;

import net.sf.saxon.om.NodeInfo;
import org.nineml.coffeefilter.InvisibleXmlParser;

import java.net.URI;
import java.util.HashMap;

public class ParserCache {
    protected HashMap<NodeInfo, InvisibleXmlParser> nodeCache = new HashMap<>();

    protected ParserCache() {
        // nop
    }

    protected void flush() {
        nodeCache.clear();
    }

}
