package org.nineml.coffeesacks;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.hof.UserFunctionReference;
import net.sf.saxon.ma.map.KeyValuePair;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.s9api.*;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import org.nineml.coffeefilter.InvisibleXmlParser;
import org.nineml.coffeegrinder.parser.Family;
import org.nineml.coffeegrinder.parser.NonterminalSymbol;
import org.nineml.coffeegrinder.trees.TreeSelector;

import java.util.List;
import java.util.Map;

public class XPathTreeSelector implements TreeSelector {
    public static final XdmAtomicValue _input = new XdmAtomicValue("input");
    public static final XdmAtomicValue _forest = new XdmAtomicValue("forest");
    public static final XdmAtomicValue _available_choices = new XdmAtomicValue("available-choices");
    public static final XdmAtomicValue _other_choices = new XdmAtomicValue("other-choices");
    public static final XdmAtomicValue _selection = new XdmAtomicValue("selection");
    public static final XdmAtomicValue _ambiguous_choice = new XdmAtomicValue("ambiguous-choice");

    protected final Processor processor;
    protected final InvisibleXmlParser parser;
    protected final XmlForest forest;
    protected final XdmAtomicValue input;
    protected XPathContext context = null;
    protected UserFunctionReference.BoundUserFunction chooseAlternatives = null;
    private boolean madeAmbiguousChoice = false;
    private XdmMap accumulator = new XdmMap();

    public XPathTreeSelector(Processor processor, InvisibleXmlParser parser, XmlForest forest, String input) {
        this.processor = processor;
        this.parser = parser;
        this.forest = forest;
        this.input = new XdmAtomicValue(input);
    }

    @Override
    public void startNonterminal(NonterminalSymbol symbol, Map<String,String> attributes, int leftExtent, int rightExtent) {
        // nop
    }

    @Override
    public void endNonterminal(NonterminalSymbol symbol, Map<String,String> attributes, int leftExtent, int rightExtent) {
        // nop
    }

    public void setChooseFunction(XPathContext context, UserFunctionReference.BoundUserFunction chooseFunction) {
        this.context = context;
        this.chooseAlternatives = chooseFunction;
    }

    @Override
    public boolean getMadeAmbiguousChoice() {
        return madeAmbiguousChoice;
    }

    @Override
    public Family select(List<Family> choices, List<Family> otherChoices) {
        if (chooseAlternatives == null) {
            return choices.get(0);
        }

        XdmNode node = forest.choiceIndex.get("C" + choices.get(0).id);
        XdmMap map = new XdmMap();
        map = map.put(_input, input);

        XdmValue seq = XdmEmptySequence.getInstance();
        for (Family choice : choices) {
            seq = seq.append(new XdmAtomicValue("C" + choice.id));
        }
        map = map.put(_available_choices, seq);

        seq = XdmEmptySequence.getInstance();
        for (Family choice : otherChoices) {
            seq = seq.append(new XdmAtomicValue("C" + choice.id));
        }
        map = map.put(_other_choices, seq);

        for (XdmAtomicValue key : accumulator.keySet()) {
            map = map.put(key, accumulator.get(key));
        }

        String selection = null;
        try {
            Sequence result = chooseAlternatives.call(context, new Sequence[] { node.getUnderlyingNode(), map.getUnderlyingValue() });
            MapItem newMap = (MapItem) result.head();
            map = new XdmMap();
            for (KeyValuePair pair : newMap.keyValuePairs()) {
                XdmAtomicValue key = new XdmAtomicValue(pair.key);
                if (!_forest.equals(key) && !_selection.equals(key)
                        && !_available_choices.equals(key) && !_other_choices.equals(key)
                        && !_ambiguous_choice.equals(key)) {
                    map = map.put(key, XdmValue.wrap(pair.value));
                }
                if (_selection.equals(key)) {
                    selection = pair.value.getStringValue();
                }
                if (!madeAmbiguousChoice && _ambiguous_choice.equals(key)) {
                    madeAmbiguousChoice = pair.value.effectiveBooleanValue();
                }
            }
            accumulator = map;

            if (selection == null) {
                throw new CoffeeSacksException(CoffeeSacksException.ERR_INVALID_CHOICE, "choose-alternative function must return a selection");
            }

            for (Family choice : choices) {
                if (selection.equals("C" + choice.id)) {
                    return choice;
                }
            }

            for (Family choice : otherChoices) {
                if (selection.equals("C" + choice.id)) {
                    return choice;
                }
            }

            throw new CoffeeSacksException(CoffeeSacksException.ERR_INVALID_CHOICE, "choose-alternative function returned invalid selection: " + selection);
        } catch (XPathException ex) {
            throw new UncheckedXPathException(ex);
        }
    }

    @Override
    public void reset() {

    }
}
