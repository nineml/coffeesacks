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
  <xsl:variable name="parser" select="cs:load-grammar('numbers.ixml',
                                      map{'choose-alternative': f:choose#1})"/>
  <doc>
    <xsl:sequence select="$parser(unparsed-text('numbers.txt'))"/>
  </doc>
</xsl:template>

<xsl:function name="f:choose" as="xs:integer">
  <xsl:param name="alternatives" as="element()+"/>
  <!-- select the alternative that contains 'decimal' -->
  <xsl:message select="$alternatives/root()!serialize(.,map{'method':'xml','indent':true()})"/>
  <xsl:sequence select="$alternatives[decimal]/@alternative"/>
</xsl:function>

</xsl:stylesheet>
