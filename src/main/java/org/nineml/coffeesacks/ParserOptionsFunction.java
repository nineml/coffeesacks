package org.nineml.coffeesacks;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.SequenceType;

import java.util.*;

/**
 * A Saxon extension function to change the parser options.
 *
 * <p>Assuming the <code>cs:</code> prefix is bound to the CoffeeSacks namespace,
 * <code>cs:parser-options($options-map)</code> sets the options specified in the map.
 * Changing the options flushes the cache.
 * </p>
 */
public class ParserOptionsFunction extends CommonDefinition {
    private static final StructuredQName qName =
            new StructuredQName("", "http://nineml.com/ns/coffeesacks", "parser-options");

    public ParserOptionsFunction(Configuration config, ParserCache cache) {
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
        return 1;
    }

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[]{SequenceType.SINGLE_ITEM};
    }

    @Override
    public SequenceType getResultType(SequenceType[] sequenceTypes) {
        return SequenceType.SINGLE_BOOLEAN;
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new OptionsCall();
    }

    private class OptionsCall extends ExtensionFunctionCall {
        @Override
        public Sequence call(XPathContext xPathContext, Sequence[] sequences) throws XPathException {
            HashMap<String,String> options;
            Item item = sequences[0].head();
            if (item instanceof MapItem) {
                options = parseMap((MapItem) item);
            } else {
                throw new XPathException("Argument to CoffeeSacks parser-options function must be a map");
            }

            Set<String> booleanOptions = new HashSet<>(Arrays.asList("ignoreTrailingWhitespace",
                    "allowUndefinedSymbols", "allowUnreachableSymbols", "allowUnproductiveSymobls",
                    "allowMultipleDefinitions", "showMarks", "showBnfNonterminals",
                    "suppressAmbiguousState", "suppressPrefixState"));

            Set<String> stringOptions = new HashSet<>(Collections.singletonList("parser"));

            boolean changed = false;
            boolean ok = true;
            for (String key : options.keySet()) {
                String value = options.get(key);
                final boolean bool;

                if (booleanOptions.contains(key) || stringOptions.contains(key)) {
                    if (booleanOptions.contains(key)) {
                        if ("true".equals(value) || "yes".equals(value) || "1".equals(value)) {
                            bool = true;
                        } else if ("false".equals(value) || "no".equals(value) || "0".equals(value)) {
                            bool = false;
                        } else {
                            parserOptions.getLogger().warn(logcategory, "Ignoring unexpected value: %s=%s", key, value);
                            ok = false;
                            continue;
                        }
                    } else {
                        bool = false; // irrelevant, but make the IDE happy
                        if ("parser".equals(key)) {
                            if (!"GLL".equals(value) && !"Earley".equals(value)) {
                                parserOptions.getLogger().warn(logcategory, "Ignoring unexpected value: %s=%s", key, value);
                                ok = false;
                                continue;
                            }
                        }
                    }

                    switch (key) {
                        case "ignoreTrailingWhitespace":
                            changed = changed || parserOptions.getIgnoreTrailingWhitespace() != bool;
                            parserOptions.setIgnoreTrailingWhitespace(bool);
                            break;
                        case "suppressAmbiguousState":
                            changed = changed || parserOptions.isSuppressedState("ambiguous") != bool;
                            if (bool) {
                                parserOptions.suppressState("ambiguous");
                            } else {
                                parserOptions.exposeState("ambiguous");
                            }
                            break;
                        case "suppressPrefixState":
                            changed = changed || parserOptions.isSuppressedState("prefix") != bool;
                            if (bool) {
                                parserOptions.suppressState("prefix");
                            } else {
                                parserOptions.exposeState("prefix");
                            }
                            break;
                        case "allowUndefinedSymbols":
                            changed = changed || parserOptions.getAllowUndefinedSymbols() != bool;
                            parserOptions.setAllowUndefinedSymbols(bool);
                            break;
                        case "allowUnreachableSymbols":
                            changed = changed || parserOptions.getAllowUnreachableSymbols() != bool;
                            parserOptions.setAllowUnreachableSymbols(bool);
                            break;
                        case "allowUnproductiveSymbols":
                            changed = changed || parserOptions.getAllowUnproductiveSymbols() != bool;
                            parserOptions.setAllowUnproductiveSymbols(bool);
                            break;
                        case "allowMultipleDefinitions":
                            changed = changed || parserOptions.getAllowMultipleDefinitions() != bool;
                            parserOptions.setAllowMultipleDefinitions(bool);
                            break;
                        case "showMarks":
                            changed = changed || parserOptions.getShowMarks() != bool;
                            parserOptions.setShowMarks(bool);
                            break;
                        case "showBnfNonterminals":
                            changed = changed || parserOptions.getShowBnfNonterminals() != bool;
                            parserOptions.setShowBnfNonterminals(bool);
                            break;
                        case "parser":
                            changed = changed || !value.equals(parserOptions.getParserType());
                            parserOptions.setParserType(value);
                            break;
                        default:
                            parserOptions.getLogger().warn(logcategory, "Ignoring unexpected option: %s", key);
                    }
                } else {
                    parserOptions.getLogger().warn(logcategory, "Ignoring unknown option: %s", key);
                    ok = false;
                }
            }

            if (changed) {
                cache.nodeCache.clear();
            }

            if (ok) {
                return BooleanValue.TRUE;
            }
            return BooleanValue.FALSE;
        }
    }
}
