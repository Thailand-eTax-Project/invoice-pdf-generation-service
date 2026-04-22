<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fo="http://www.w3.org/1999/XSL/Format"
    xmlns:rsm="urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2"
    xmlns:ram="urn:etda:uncefact:data:standard:Invoice_ReusableAggregateBusinessInformationEntity:2">

    <xsl:output method="xml" indent="yes"/>

    <!-- Injected by Java: ThaiAmountWordsConverter output -->
    <xsl:param name="amountInWords"/>

    <xsl:variable name="page-width">210mm</xsl:variable>
    <xsl:variable name="page-height">297mm</xsl:variable>
    <xsl:variable name="margin">15mm</xsl:variable>

    <xsl:variable name="font-family">THSarabunNew, NotoSansThai, Helvetica, sans-serif</xsl:variable>
    <xsl:variable name="font-size">11pt</xsl:variable>
    <xsl:variable name="font-size-small">9pt</xsl:variable>
    <xsl:variable name="font-size-large">14pt</xsl:variable>
    <xsl:variable name="font-size-title">18pt</xsl:variable>

    <!-- Global variables using absolute paths from document root -->
    <xsl:variable name="headerAgreement"
        select="/rsm:Invoice_CrossIndustryInvoice/rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement"/>
    <xsl:variable name="headerSettlement"
        select="/rsm:Invoice_CrossIndustryInvoice/rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeSettlement"/>
    <xsl:variable name="monetarySummation"
        select="$headerSettlement/ram:SpecifiedTradeSettlementHeaderMonetarySummation"/>

    <xsl:template match="/rsm:Invoice_CrossIndustryInvoice">
        <fo:root>
            <fo:layout-master-set>
                <fo:simple-page-master master-name="invoice-page"
                    page-width="{$page-width}" page-height="{$page-height}"
                    margin-top="{$margin}" margin-bottom="{$margin}"
                    margin-left="{$margin}" margin-right="{$margin}">
                    <fo:region-body margin-top="20mm" margin-bottom="20mm"/>
                    <fo:region-before extent="20mm"/>
                    <fo:region-after extent="20mm"/>
                </fo:simple-page-master>
            </fo:layout-master-set>

            <fo:page-sequence master-reference="invoice-page">
                <fo:static-content flow-name="xsl-region-before">
                    <xsl:call-template name="header"/>
                </fo:static-content>
                <fo:static-content flow-name="xsl-region-after">
                    <xsl:call-template name="footer"/>
                </fo:static-content>
                <fo:flow flow-name="xsl-region-body">
                    <xsl:call-template name="invoice-title"/>
                    <xsl:call-template name="parties-info"/>
                    <xsl:call-template name="invoice-details"/>
                    <xsl:call-template name="line-items"/>
                    <xsl:call-template name="totals"/>
                    <xsl:call-template name="amount-in-words"/>
                </fo:flow>
            </fo:page-sequence>
        </fo:root>
    </xsl:template>

    <!-- Header -->
    <xsl:template name="header">
        <fo:block font-family="{$font-family}" font-size="{$font-size-small}" color="#666666"
            border-bottom="0.5pt solid #cccccc" padding-bottom="2mm">
            <fo:table width="100%" table-layout="fixed">
                <fo:table-column column-width="50%"/>
                <fo:table-column column-width="50%"/>
                <fo:table-body>
                    <fo:table-row>
                        <fo:table-cell>
                            <fo:block text-align="left">
                                <xsl:value-of select="$headerAgreement/ram:SellerTradeParty/ram:Name"/>
                            </fo:block>
                        </fo:table-cell>
                        <fo:table-cell>
                            <fo:block text-align="right">
                                e-Tax Invoice / &#x0E43;&#x0E1A;&#x0E41;&#x0E08;&#x0E49;&#x0E07;&#x0E2B;&#x0E19;&#x0E35;&#x0E49;&#x0E2D;&#x0E34;&#x0E40;&#x0E25;&#x0E47;&#x0E01;&#x0E17;&#x0E23;&#x0E2D;&#x0E19;&#x0E34;&#x0E01;&#x0E2A;&#x0E4C;
                            </fo:block>
                        </fo:table-cell>
                    </fo:table-row>
                </fo:table-body>
            </fo:table>
        </fo:block>
    </xsl:template>

    <!-- Footer -->
    <xsl:template name="footer">
        <fo:block font-family="{$font-family}" font-size="{$font-size-small}" color="#666666"
            border-top="0.5pt solid #cccccc" padding-top="2mm">
            <fo:table width="100%" table-layout="fixed">
                <fo:table-column column-width="33%"/>
                <fo:table-column column-width="34%"/>
                <fo:table-column column-width="33%"/>
                <fo:table-body>
                    <fo:table-row>
                        <fo:table-cell>
                            <fo:block text-align="left">
                                &#x0E40;&#x0E2D;&#x0E01;&#x0E2A;&#x0E32;&#x0E23;&#x0E19;&#x0E35;&#x0E49;&#x0E08;&#x0E31;&#x0E14;&#x0E17;&#x0E33;&#x0E14;&#x0E49;&#x0E27;&#x0E22;&#x0E23;&#x0E30;&#x0E1A;&#x0E1A;&#x0E04;&#x0E2D;&#x0E21;&#x0E1E;&#x0E34;&#x0E27;&#x0E40;&#x0E15;&#x0E2D;&#x0E23;&#x0E4C;
                            </fo:block>
                        </fo:table-cell>
                        <fo:table-cell>
                            <fo:block text-align="center">
                                &#x0E2B;&#x0E19;&#x0E49;&#x0E32; <fo:page-number/> / <fo:page-number-citation ref-id="last-page"/>
                            </fo:block>
                        </fo:table-cell>
                        <fo:table-cell>
                            <fo:block text-align="right">
                                <xsl:value-of select="rsm:ExchangedDocument/ram:ID"/>
                            </fo:block>
                        </fo:table-cell>
                    </fo:table-row>
                </fo:table-body>
            </fo:table>
        </fo:block>
    </xsl:template>

    <!-- Invoice title -->
    <xsl:template name="invoice-title">
        <fo:block font-family="{$font-family}" font-size="{$font-size-title}" font-weight="bold"
            text-align="center" space-after="5mm" color="#333333">
            &#x0E43;&#x0E1A;&#x0E41;&#x0E08;&#x0E49;&#x0E07;&#x0E2B;&#x0E19;&#x0E35;&#x0E49; / INVOICE
        </fo:block>
        <fo:block font-family="{$font-family}" font-size="{$font-size}" text-align="center"
            space-after="10mm" color="#666666">
            (&#x0E15;&#x0E49;&#x0E19;&#x0E09;&#x0E1A;&#x0E31;&#x0E1A; / Original)
        </fo:block>
    </xsl:template>

    <!-- Parties info -->
    <xsl:template name="parties-info">
        <xsl:variable name="seller" select="$headerAgreement/ram:SellerTradeParty"/>
        <xsl:variable name="buyer" select="$headerAgreement/ram:BuyerTradeParty"/>
        <fo:table width="100%" table-layout="fixed" space-after="8mm">
            <fo:table-column column-width="50%"/>
            <fo:table-column column-width="50%"/>
            <fo:table-body>
                <fo:table-row>
                    <!-- Seller -->
                    <fo:table-cell padding-right="5mm">
                        <fo:block font-family="{$font-family}" font-size="{$font-size}"
                            background-color="#f5f5f5" padding="3mm" border="0.5pt solid #dddddd">
                            <fo:block font-weight="bold" space-after="2mm">&#x0E1C;&#x0E39;&#x0E49;&#x0E02;&#x0E32;&#x0E22; / Seller</fo:block>
                            <fo:block><xsl:value-of select="$seller/ram:Name"/></fo:block>
                            <fo:block>
                                <xsl:value-of select="$seller/ram:PostalTradeAddress/ram:BuildingNumber"/>
                                <xsl:text> </xsl:text>
                                <xsl:value-of select="$seller/ram:PostalTradeAddress/ram:CitySubDivisionName"/>
                                <xsl:text> </xsl:text>
                                <xsl:value-of select="$seller/ram:PostalTradeAddress/ram:CityName"/>
                                <xsl:text> </xsl:text>
                                <xsl:value-of select="$seller/ram:PostalTradeAddress/ram:PostcodeCode"/>
                            </fo:block>
                            <fo:block>
                                &#x0E40;&#x0E25;&#x0E02;&#x0E1B;&#x0E23;&#x0E30;&#x0E08;&#x0E33;&#x0E15;&#x0E31;&#x0E27;&#x0E1C;&#x0E39;&#x0E49;&#x0E40;&#x0E2A;&#x0E35;&#x0E22;&#x0E20;&#x0E32;&#x0E29;&#x0E35;: <xsl:value-of select="$seller/ram:SpecifiedTaxRegistration/ram:ID"/>
                            </fo:block>
                            <xsl:if test="$seller/ram:DefinedTradeContact/ram:TelephoneUniversalCommunication/ram:CompleteNumber">
                                <fo:block>&#x0E42;&#x0E17;&#x0E23;: <xsl:value-of select="$seller/ram:DefinedTradeContact/ram:TelephoneUniversalCommunication/ram:CompleteNumber"/></fo:block>
                            </xsl:if>
                            <xsl:if test="$seller/ram:DefinedTradeContact/ram:EmailURIUniversalCommunication/ram:URIID">
                                <fo:block>&#x0E2D;&#x0E35;&#x0E40;&#x0E21;&#x0E25;&#x0E4C;: <xsl:value-of select="$seller/ram:DefinedTradeContact/ram:EmailURIUniversalCommunication/ram:URIID"/></fo:block>
                            </xsl:if>
                        </fo:block>
                    </fo:table-cell>
                    <!-- Buyer -->
                    <fo:table-cell padding-left="5mm">
                        <fo:block font-family="{$font-family}" font-size="{$font-size}"
                            background-color="#f5f5f5" padding="3mm" border="0.5pt solid #dddddd">
                            <fo:block font-weight="bold" space-after="2mm">&#x0E1C;&#x0E39;&#x0E49;&#x0E0B;&#x0E37;&#x0E49;&#x0E2D; / Buyer</fo:block>
                            <fo:block><xsl:value-of select="$buyer/ram:Name"/></fo:block>
                            <fo:block>
                                <xsl:value-of select="$buyer/ram:PostalTradeAddress/ram:BuildingNumber"/>
                                <xsl:text> </xsl:text>
                                <xsl:value-of select="$buyer/ram:PostalTradeAddress/ram:CitySubDivisionName"/>
                                <xsl:text> </xsl:text>
                                <xsl:value-of select="$buyer/ram:PostalTradeAddress/ram:CityName"/>
                                <xsl:text> </xsl:text>
                                <xsl:value-of select="$buyer/ram:PostalTradeAddress/ram:PostcodeCode"/>
                            </fo:block>
                            <fo:block>
                                &#x0E40;&#x0E25;&#x0E02;&#x0E1B;&#x0E23;&#x0E30;&#x0E08;&#x0E33;&#x0E15;&#x0E31;&#x0E27;&#x0E1C;&#x0E39;&#x0E49;&#x0E40;&#x0E2A;&#x0E35;&#x0E22;&#x0E20;&#x0E32;&#x0E29;&#x0E35;: <xsl:value-of select="$buyer/ram:SpecifiedTaxRegistration/ram:ID"/>
                            </fo:block>
                            <xsl:if test="$buyer/ram:DefinedTradeContact/ram:TelephoneUniversalCommunication/ram:CompleteNumber">
                                <fo:block>&#x0E42;&#x0E17;&#x0E23;: <xsl:value-of select="$buyer/ram:DefinedTradeContact/ram:TelephoneUniversalCommunication/ram:CompleteNumber"/></fo:block>
                            </xsl:if>
                            <xsl:if test="$buyer/ram:DefinedTradeContact/ram:EmailURIUniversalCommunication/ram:URIID">
                                <fo:block>&#x0E2D;&#x0E35;&#x0E40;&#x0E21;&#x0E25;&#x0E4C;: <xsl:value-of select="$buyer/ram:DefinedTradeContact/ram:EmailURIUniversalCommunication/ram:URIID"/></fo:block>
                            </xsl:if>
                        </fo:block>
                    </fo:table-cell>
                </fo:table-row>
            </fo:table-body>
        </fo:table>
    </xsl:template>

    <!-- Invoice details -->
    <xsl:template name="invoice-details">
        <fo:table width="100%" table-layout="fixed" space-after="8mm"
            font-family="{$font-family}" font-size="{$font-size}">
            <fo:table-column column-width="25%"/>
            <fo:table-column column-width="25%"/>
            <fo:table-column column-width="25%"/>
            <fo:table-column column-width="25%"/>
            <fo:table-body>
                <fo:table-row>
                    <fo:table-cell padding="2mm" border="0.5pt solid #dddddd" background-color="#e8e8e8">
                        <fo:block font-weight="bold">&#x0E40;&#x0E25;&#x0E02;&#x0E17;&#x0E35;&#x0E48;&#x0E40;&#x0E2D;&#x0E01;&#x0E2A;&#x0E32;&#x0E23;</fo:block>
                        <fo:block font-size="{$font-size-small}">Invoice No.</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                        <fo:block><xsl:value-of select="rsm:ExchangedDocument/ram:ID"/></fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="2mm" border="0.5pt solid #dddddd" background-color="#e8e8e8">
                        <fo:block font-weight="bold">&#x0E27;&#x0E31;&#x0E19;&#x0E17;&#x0E35;&#x0E48;</fo:block>
                        <fo:block font-size="{$font-size-small}">Date</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                        <fo:block><xsl:value-of select="substring(rsm:ExchangedDocument/ram:IssueDateTime, 1, 10)"/></fo:block>
                    </fo:table-cell>
                </fo:table-row>
                <xsl:if test="$headerSettlement/ram:SpecifiedTradePaymentTerms/ram:DueDateDateTime">
                    <fo:table-row>
                        <fo:table-cell padding="2mm" border="0.5pt solid #dddddd" background-color="#e8e8e8">
                            <fo:block font-weight="bold">&#x0E27;&#x0E31;&#x0E19;&#x0E04;&#x0E23;&#x0E1A;&#x0E01;&#x0E33;&#x0E2B;&#x0E19;&#x0E14;&#x0E0A;&#x0E33;&#x0E23;&#x0E30;</fo:block>
                            <fo:block font-size="{$font-size-small}">Due Date</fo:block>
                        </fo:table-cell>
                        <fo:table-cell padding="2mm" border="0.5pt solid #dddddd" number-columns-spanned="3">
                            <fo:block>
                                <xsl:value-of select="substring($headerSettlement/ram:SpecifiedTradePaymentTerms/ram:DueDateDateTime, 1, 10)"/>
                            </fo:block>
                        </fo:table-cell>
                    </fo:table-row>
                </xsl:if>
            </fo:table-body>
        </fo:table>
    </xsl:template>

    <!-- Line items -->
    <xsl:template name="line-items">
        <fo:table width="100%" table-layout="fixed" space-after="5mm"
            font-family="{$font-family}" font-size="{$font-size}">
            <fo:table-column column-width="8%"/>
            <fo:table-column column-width="37%"/>
            <fo:table-column column-width="12%"/>
            <fo:table-column column-width="10%"/>
            <fo:table-column column-width="15%"/>
            <fo:table-column column-width="18%"/>

            <fo:table-header>
                <fo:table-row background-color="#4a4a4a" color="white" font-weight="bold">
                    <fo:table-cell padding="3mm" border="0.5pt solid #333333">
                        <fo:block text-align="center">&#x0E25;&#x0E33;&#x0E14;&#x0E31;&#x0E1A;</fo:block>
                        <fo:block text-align="center" font-size="{$font-size-small}">No.</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="3mm" border="0.5pt solid #333333">
                        <fo:block>&#x0E23;&#x0E32;&#x0E22;&#x0E01;&#x0E32;&#x0E23;</fo:block>
                        <fo:block font-size="{$font-size-small}">Description</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="3mm" border="0.5pt solid #333333">
                        <fo:block text-align="right">&#x0E08;&#x0E33;&#x0E19;&#x0E27;&#x0E19;</fo:block>
                        <fo:block text-align="right" font-size="{$font-size-small}">Qty</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="3mm" border="0.5pt solid #333333">
                        <fo:block text-align="center">&#x0E2B;&#x0E19;&#x0E48;&#x0E27;&#x0E22;</fo:block>
                        <fo:block text-align="center" font-size="{$font-size-small}">Unit</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="3mm" border="0.5pt solid #333333">
                        <fo:block text-align="right">&#x0E23;&#x0E32;&#x0E04;&#x0E32;/&#x0E2B;&#x0E19;&#x0E48;&#x0E27;&#x0E22;</fo:block>
                        <fo:block text-align="right" font-size="{$font-size-small}">Unit Price</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="3mm" border="0.5pt solid #333333">
                        <fo:block text-align="right">&#x0E08;&#x0E33;&#x0E19;&#x0E27;&#x0E19;&#x0E40;&#x0E07;&#x0E34;&#x0E19;</fo:block>
                        <fo:block text-align="right" font-size="{$font-size-small}">Amount</fo:block>
                    </fo:table-cell>
                </fo:table-row>
            </fo:table-header>

            <fo:table-body>
                <xsl:for-each select="rsm:SupplyChainTradeTransaction/rsm:IncludedSupplyChainTradeLineItem">
                    <fo:table-row>
                        <xsl:attribute name="background-color">
                            <xsl:choose>
                                <xsl:when test="position() mod 2 = 0">#f9f9f9</xsl:when>
                                <xsl:otherwise>white</xsl:otherwise>
                            </xsl:choose>
                        </xsl:attribute>
                        <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                            <fo:block text-align="center"><xsl:value-of select="position()"/></fo:block>
                        </fo:table-cell>
                        <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                            <fo:block><xsl:value-of select="ram:SpecifiedTradeProduct/ram:Name"/></fo:block>
                            <xsl:if test="ram:AssociatedDocumentLineDocument/ram:LineID">
                                <fo:block font-size="{$font-size-small}" color="#666666">
                                    &#x0E23;&#x0E2B;&#x0E31;&#x0E2A;: <xsl:value-of select="ram:AssociatedDocumentLineDocument/ram:LineID"/>
                                </fo:block>
                            </xsl:if>
                        </fo:table-cell>
                        <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                            <fo:block text-align="right">
                                <xsl:value-of select="format-number(ram:SpecifiedLineTradeDelivery/ram:BilledQuantity, '#,##0.00')"/>
                            </fo:block>
                        </fo:table-cell>
                        <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                            <fo:block text-align="center">
                                <xsl:value-of select="ram:SpecifiedLineTradeDelivery/ram:BilledQuantity/@unitCode"/>
                            </fo:block>
                        </fo:table-cell>
                        <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                            <fo:block text-align="right">
                                <xsl:value-of select="format-number(ram:SpecifiedLineTradeAgreement/ram:NetPriceProductTradePrice/ram:ChargeAmount, '#,##0.00')"/>
                            </fo:block>
                        </fo:table-cell>
                        <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                            <fo:block text-align="right">
                                <xsl:value-of select="format-number(ram:SpecifiedLineTradeSettlement/ram:SpecifiedTradeSettlementLineMonetarySummation/ram:NetLineTotalAmount, '#,##0.00')"/>
                            </fo:block>
                        </fo:table-cell>
                    </fo:table-row>
                </xsl:for-each>
            </fo:table-body>
        </fo:table>
    </xsl:template>

    <!-- Totals -->
    <xsl:template name="totals">
        <fo:table width="100%" table-layout="fixed" space-after="8mm"
            font-family="{$font-family}" font-size="{$font-size}">
            <fo:table-column column-width="60%"/>
            <fo:table-column column-width="22%"/>
            <fo:table-column column-width="18%"/>
            <fo:table-body>
                <!-- Subtotal -->
                <fo:table-row>
                    <fo:table-cell><fo:block></fo:block></fo:table-cell>
                    <fo:table-cell padding="2mm" border="0.5pt solid #dddddd" background-color="#f5f5f5">
                        <fo:block text-align="right">&#x0E23;&#x0E27;&#x0E21;&#x0E40;&#x0E07;&#x0E34;&#x0E19; / Subtotal</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                        <fo:block text-align="right">
                            <xsl:value-of select="format-number($monetarySummation/ram:LineTotalAmount, '#,##0.00')"/>
                        </fo:block>
                    </fo:table-cell>
                </fo:table-row>

                <!-- VAT -->
                <fo:table-row>
                    <fo:table-cell><fo:block></fo:block></fo:table-cell>
                    <fo:table-cell padding="2mm" border="0.5pt solid #dddddd" background-color="#f5f5f5">
                        <fo:block text-align="right">
                            &#x0E20;&#x0E32;&#x0E29;&#x0E35;&#x0E21;&#x0E39;&#x0E25;&#x0E04;&#x0E48;&#x0E32;&#x0E40;&#x0E1E;&#x0E34;&#x0E48;&#x0E21; <xsl:value-of select="$headerSettlement/ram:ApplicableTradeTax/ram:CalculatedRate"/>% / VAT
                        </fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                        <fo:block text-align="right">
                            <xsl:value-of select="format-number($headerSettlement/ram:ApplicableTradeTax/ram:CalculatedAmount, '#,##0.00')"/>
                        </fo:block>
                    </fo:table-cell>
                </fo:table-row>

                <!-- Grand Total -->
                <fo:table-row font-weight="bold" font-size="{$font-size-large}">
                    <fo:table-cell><fo:block></fo:block></fo:table-cell>
                    <fo:table-cell padding="3mm" border="0.5pt solid #333333" background-color="#4a4a4a" color="white">
                        <fo:block text-align="right">&#x0E22;&#x0E2D;&#x0E14;&#x0E23;&#x0E27;&#x0E21;&#x0E17;&#x0E31;&#x0E49;&#x0E07;&#x0E2A;&#x0E34;&#x0E49;&#x0E19; / Grand Total</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="3mm" border="0.5pt solid #333333" background-color="#f0f0f0">
                        <fo:block text-align="right">
                            <xsl:value-of select="format-number($monetarySummation/ram:GrandTotalAmount, '#,##0.00')"/>
                        </fo:block>
                    </fo:table-cell>
                </fo:table-row>
            </fo:table-body>
        </fo:table>

        <!-- End marker for page counting -->
        <fo:block id="last-page"/>
    </xsl:template>

    <!-- Amount in words (XSLT parameter) -->
    <xsl:template name="amount-in-words">
        <xsl:if test="$amountInWords != ''">
            <fo:block font-family="{$font-family}" font-size="{$font-size}" space-after="5mm"
                padding="3mm" background-color="#fffde7" border="0.5pt solid #ffc107">
                <fo:inline font-weight="bold">&#x0E08;&#x0E33;&#x0E19;&#x0E27;&#x0E19;&#x0E40;&#x0E07;&#x0E34;&#x0E19;&#x0E40;&#x0E1B;&#x0E47;&#x0E19;&#x0E15;&#x0E31;&#x0E27;&#x0E2D;&#x0E31;&#x0E01;&#x0E29;&#x0E23;: </fo:inline>
                <xsl:value-of select="$amountInWords"/>
            </fo:block>
        </xsl:if>
    </xsl:template>

</xsl:stylesheet>
