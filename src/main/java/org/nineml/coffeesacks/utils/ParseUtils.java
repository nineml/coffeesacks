package org.nineml.coffeesacks.utils;

import net.sf.saxon.ma.map.KeyValuePair;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.QNameValue;

import java.util.HashMap;

public class ParseUtils {
    private static Boolean isWindows = null;

    public static HashMap<QName,String> parseMap(MapItem item) throws XPathException {
        HashMap<QName,String> options = new HashMap<>();
        for (KeyValuePair kv : item.keyValuePairs()) {
            QName key = null;
            if (kv.key.getItemType() == BuiltInAtomicType.QNAME) {
                QNameValue qkey = (QNameValue) kv.key;
                key = new QName(qkey.getPrefix(), qkey.getNamespaceURI(), qkey.getLocalName());
            } else {
                key = new QName("", "", kv.key.getStringValue());
            }
            String value = kv.value.getStringValue();
            options.put(key, value);
        }
        return options;
    }
}
