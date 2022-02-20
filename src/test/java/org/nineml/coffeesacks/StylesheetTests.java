package org.nineml.coffeesacks;

import net.sf.saxon.s9api.XdmNode;
import org.junit.Assert;
import org.junit.Test;

public class StylesheetTests extends TestConfiguration {
    @Test
    public void stringInputXmlOutput() {
        XdmNode stylesheet = loadStylesheet("src/test/resources/date-xml-string.xsl");
        XdmNode result = transform(stylesheet, stylesheet);
        Assert.assertEquals("<doc><date><day>15</day><month>February</month><year>2022</year></date></doc>", serialize(result));
    }

    @Test
    public void uriInputXmlOutput() {
        XdmNode stylesheet = loadStylesheet("src/test/resources/date-xml-uri.xsl");
        XdmNode result = transform(stylesheet, stylesheet);
        Assert.assertEquals("<doc><date><day>20</day><month>February</month><year>2022</year></date></doc>", serialize(result));
    }

    @Test
    public void stringInputMapOutput() {
        XdmNode stylesheet = loadStylesheet("src/test/resources/date-map-string.xsl");
        XdmNode result = transform(stylesheet, stylesheet);
        Assert.assertEquals("<doc>year: 2022, month: February, day: 15</doc>", serialize(result));
    }

    @Test
    public void uriInputMapOutput() {
        XdmNode stylesheet = loadStylesheet("src/test/resources/date-map-uri.xsl");
        XdmNode result = transform(stylesheet, stylesheet);
        Assert.assertEquals("<doc>year: 2022, month: February, day: 20</doc>", serialize(result));
    }

    @Test
    public void clearCache() {
        XdmNode stylesheet = loadStylesheet("src/test/resources/clear-cache.xsl");
        XdmNode result = transform(stylesheet, stylesheet);
        Assert.assertEquals("<doc>true</doc>", serialize(result));
    }

}
