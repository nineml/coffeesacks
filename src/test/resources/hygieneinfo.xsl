<?xml version="1.0" encoding="utf-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:cs="http://nineml.com/ns/coffeesacks"
                xmlns:map="http://www.w3.org/2005/xpath-functions/map"
                exclude-result-prefixes="#all"
                version="3.0">

<xsl:output method="xml" encoding="utf-8" indent="no"/>

<xsl:mode on-no-match="shallow-copy"/>

<xsl:template match="/">
  <xsl:choose>
    <xsl:when test="cs:parser-options(map{'allowUndefinedSymbols': 'true'})">
      <xsl:variable name="grammar" select="cs:grammar-uri('messy.ixml')"/>
      <xsl:sequence select="cs:hygiene-report($grammar)"/>
    </xsl:when>
    <xsl:otherwise>
      <xsl:message terminate="yes"
                   select="'Failed to set parser option: allowUndefinedSymbols'"/>
    </xsl:otherwise>
  </xsl:choose>
</xsl:template>

</xsl:stylesheet>
