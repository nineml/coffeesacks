<?xml version="1.0" encoding="utf-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:cs="http://nineml.com/ns/coffeesacks"
                xmlns:f="http://coffeesacks.nineml.com/fn/testing"
                xmlns:map="http://www.w3.org/2005/xpath-functions/map"
                exclude-result-prefixes="#all"
                version="3.0">

<xsl:output method="xml" encoding="utf-8" indent="no"/>

<xsl:mode on-no-match="shallow-copy"/>

<xsl:template match="/">
  <xsl:variable name="grammar" select="'s = a | b. a = ''a''. b = ''a''.'"/>
  <xsl:variable name="parser" select="cs:make-parser($grammar, map{'choose-alternative': f:choose#1})"/>
  <doc>
    <xsl:sequence select="$parser('a')"/>
  </doc>
</xsl:template>

<xsl:function name="f:choose" as="xs:integer">
  <xsl:param name="alternatives" as="element()+"/>
  <!-- select the alternative that contains only a single 'n' -->
  <!--<xsl:message select="$alternatives/root()!serialize(.,map{'method':'xml','indent':true()})"/>-->
  <xsl:sequence select="$alternatives[b]/@alternative"/>
</xsl:function>

</xsl:stylesheet>
