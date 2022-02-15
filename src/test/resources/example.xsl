<?xml version="1.0" encoding="utf-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:cs="http://nineml.com/ns/coffeesacks"
                exclude-result-prefixes="#all"
                version="3.0">

<xsl:template name="xsl:initial-template">
  <xsl:variable name="grammar" select="cs:grammar('date.ixml')"/>
  <doc>
    <xsl:sequence select="cs:parse-string($grammar, '15 February 2022')"/>
  </doc>
</xsl:template>

</xsl:stylesheet>
