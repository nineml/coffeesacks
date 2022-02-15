package org.nineml.coffeesacks;

import net.sf.saxon.om.NodeInfo;
import org.nineml.coffeefilter.InvisibleXmlParser;

import java.net.URI;
import java.util.HashMap;

/**
 * A parser cache.
 * <p>Loading Invisible XML grammars can be expensive. By default the CoffeeSacks functions
 * maintain a cache of grammars that have been loaded. You can clear the cache wcacheith
 * <code>cs:clear-cache()</code>. You can prevent grammars from being added to the
 * cache by setting the <code>cache</code> option in the options map to "false".</p>
 */
public class ParserCache {
    protected HashMap<NodeInfo, InvisibleXmlParser> nodeCache = new HashMap<>();
    protected HashMap<URI, NodeInfo> uriCache = new HashMap<>();

    protected ParserCache() {
        // nop
    }

    protected void clear() {
        nodeCache.clear();
        uriCache.clear();
    }

}
