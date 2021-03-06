package org.nineml.coffeesacks;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.Initializer;

import javax.xml.transform.TransformerException;

/**
 * An initializer class for registering the CoffeeSacks extension functions.
 */
public class RegisterCoffeeSacks implements Initializer {
    @Override
    public void initialize(Configuration config) throws TransformerException {
        ParserCache cache = new ParserCache();
        config.registerExtensionFunction(new GrammarStringFunction(config, cache));
        config.registerExtensionFunction(new GrammarUriFunction(config, cache));
        config.registerExtensionFunction(new ParseStringFunction(config, cache));
        config.registerExtensionFunction(new ParseUriFunction(config, cache));
        config.registerExtensionFunction(new ClearCacheFunction(config, cache));
        config.registerExtensionFunction(new HygieneReportFunction(config, cache));
        config.registerExtensionFunction(new ParserOptionsFunction(config, cache));
    }
}
