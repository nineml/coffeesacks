package org.nineml.coffeesacks;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.Initializer;

import javax.xml.transform.TransformerException;

public class RegisterCoffeeSacks implements Initializer {
    @Override
    public void initialize(Configuration config) throws TransformerException {
        ParserCache cache = new ParserCache();
        config.registerExtensionFunction(new GrammarFunction(cache));
        config.registerExtensionFunction(new ParseStringFunction(cache));
        config.registerExtensionFunction(new ParseUriFunction(cache));
        config.registerExtensionFunction(new ClearCacheFunction(cache));
    }
}
