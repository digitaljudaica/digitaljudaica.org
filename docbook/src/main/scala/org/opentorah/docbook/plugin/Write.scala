package org.opentorah.docbook.plugin

import org.opentorah.docbook.section.{DocBook2, HtmlCommon, Section}
import org.opentorah.fop.xml.{Namespace, Xml}
import org.opentorah.util.Json

object Write {

  val defaultInputFile: String =
    s"""${Xml.header}
       |${DocBook.doctype}
       |
       |<article ${DocBook.Namespace.withVersion}
       |         ${Namespace.XInclude}>
       |</article>
       |""".stripMargin

  def xmlCatalog(layout: Layout): String = {
    val data: String = layout.dataDirectoryRelative

    s"""${Xml.header}
       |<!DOCTYPE catalog
       |  PUBLIC "-//OASIS//DTD XML Catalogs V1.1//EN"
       |  "http://www.oasis-open.org/committees/entity/release/1.1/catalog.dtd">
       |
       |<!-- DO NOT EDIT! Generated by the DocBook plugin.
       |     Customizations go into ${layout.catalogCustomFileName}. -->
       |<catalog xmlns="urn:oasis:names:tc:entity:xmlns:xml:catalog" prefer="public">
       |  <group xml:base="${layout.catalogGroupBase}">
       |    <!--
       |      There seems to be some confusion with the rewriteURI form:
       |      Catalog DTD requires 'uriIdStartString' attribute (and that is what IntelliJ wants),
       |      but XMLResolver looks for the 'uriStartString' attribute (and this seems to work in Oxygen).
       |    -->
       |
       |    <!-- DocBook XSLT 1.0 stylesheets  -->
       |    <rewriteURI uriStartString="http://docbook.sourceforge.net/release/xsl-ns/current/"
       |                rewritePrefix="${layout.docBookXslDirectoryRelative(Stylesheets.xslt1.directoryName)}"/>
       |
       |    <!-- DocBook XSLT 2.0 stylesheets  -->
       |    <rewriteURI uriStartString="https://cdn.docbook.org/release/latest/xslt/"
       |                rewritePrefix="${layout.docBookXslDirectoryRelative(Stylesheets.xslt2.directoryName)}"/>
       |
       |    <!-- generated data -->
       |    <rewriteSystem systemIdStartString="data:/"
       |                   rewritePrefix="$data"/>
       |    <rewriteSystem systemIdStartString="data:"
       |                   rewritePrefix="$data"/>
       |    <rewriteSystem systemIdStartString="urn:docbook:data:/"
       |                   rewritePrefix="$data"/>
       |    <rewriteSystem systemIdStartString="urn:docbook:data:"
       |                   rewritePrefix="$data"/>
       |    <rewriteSystem systemIdStartString="urn:docbook:data/"
       |                   rewritePrefix="$data"/>
       |    <rewriteSystem systemIdStartString="http://podval.org/docbook/data/"
       |                   rewritePrefix="$data"/>
       |  </group>
       |
       |  <!-- substitutions DTD -->
       |  <public publicId="${DocBook.dtdId}"
       |          uri="${layout.substitutionsDtdFileName}"/>
       |
       |  <nextCatalog catalog="${layout.catalogCustomFileName}"/>
       |</catalog>
       |""".stripMargin
  }

  val catalogCustomization: String =
    s"""${Xml.header}
       |<!DOCTYPE catalog
       |  PUBLIC "-//OASIS//DTD XML Catalogs V1.1//EN"
       |  "http://www.oasis-open.org/committees/entity/release/1.1/catalog.dtd">
       |
       |<!-- Customizations go here. -->
       |<catalog xmlns="urn:oasis:names:tc:entity:xmlns:xml:catalog" prefer="public">
       |  <nextCatalog catalog="/etc/xml/catalog"/>
       |</catalog>
       |""".stripMargin

  def mainStylesheet(
    docBook2: DocBook2,
    prefixed: Boolean,
    documentName: String,
    cssFileName: String,
    epubEmbeddedFonts: String,
    mathJaxConfiguration: org.opentorah.fop.mathjax.Configuration,
    layout: Layout
  ): String = {
    val forDocument = layout.forDocument(prefixed, documentName)

/////    val mainStylesheetName: String = forDocument.mainStylesheet(docBook2)
    val paramsStylesheetName: String = layout.paramsStylesheet(docBook2)
    val stylesheetUri: String = s"${Stylesheets(docBook2.usesDocBookXslt2).uri}/${docBook2.stylesheetUriName}.xsl"

    val nonOverridableParameters: Map[String, String] = Seq[Option[(String, String)]](
      Some("img.src.path", layout.imagesDirectoryName + "/"),
      docBook2.parameter(_.baseDirParameter, forDocument.baseDir(docBook2)),
      docBook2.parameter(_.rootFilenameParameter, docBook2.rootFilename(documentName)),
      docBook2.parameter(_.epubEmbeddedFontsParameter, epubEmbeddedFonts),
      docBook2.parameter(_.htmlStylesheetsParameter, layout.cssFileRelativeToOutputDirectory(cssFileName)),
      docBook2.parameter(_.mathJaxConfigurationParameter, Json.fromMap(mathJaxConfiguration.toHtmlMap))
    ).flatten.toMap

    // xsl:param has the last value assigned to it, so customization must come last;
    // since it is imported (so as not to be overwritten), and import elements must come first,
    // a separate "-param" file is written with the "default" values for the parameters :)

    val imports: String = docBook2.parameterSections.map(section =>
      s"""  <xsl:import href="${layout.customStylesheet(section)}"/>"""
    ).mkString("\n")

    s"""${Xml.header}
       |<!-- DO NOT EDIT! Generated by the DocBook plugin. -->
       |<xsl:stylesheet ${xsl(docBook2)}>
       |  <xsl:import href="$stylesheetUri"/>
       |  <xsl:import href="$paramsStylesheetName"/>
       |$imports
       |
       |${toString(nonOverridableParameters)}
       |</xsl:stylesheet>
       |""".stripMargin
  }

  private def toString(parameters: Map[String, String]): String = parameters.map { case (name: String, value: String) =>
    if (value.nonEmpty) s"""  <xsl:param name="$name">$value</xsl:param>"""
    else s"""  <xsl:param name="$name"/>"""
  }.mkString("\n")

  def paramsStylesheet(
    docBook2: DocBook2,
    sections: Map[Section, Map[String, String]],
    isInfoEnabled: Boolean
  ): String = {

    val dynamicParameters: Map[Section, Map[String, String]] = Map.empty
      .updated(HtmlCommon, Seq[Option[(String, String)]](
        if (isInfoEnabled) None else
          docBook2.parameter(_.chunkQuietlyParameter, "1")
      ).flatten.toMap)

    val parameters: Map[Section, Map[String, String]] = docBook2.parameterSections.map { section: Section => section -> (
      section.defaultParameters ++
        dynamicParameters.getOrElse(section, Map.empty) ++
        sections.getOrElse(section, Map.empty)
      )}.toMap
    //      .reduceLeft[Map[String, String]] { case (result, current) => result ++ current }

    val parametersStr: String = docBook2.parameterSections.map { section: Section =>
      s"  <!-- ${section.name} -->\n" + toString(parameters.getOrElse(section, Map.empty))
    }.mkString("\n")

    s"""${Xml.header}
       |<!-- DO NOT EDIT! Generated by the DocBook plugin. -->
       |<xsl:stylesheet ${xsl(docBook2)}>
       |$parametersStr
       |</xsl:stylesheet>
       |""".stripMargin
  }

  def customStylesheet(layout: Layout, section: Section): String =
    s"""${Xml.header}
       |<!-- Customizations go here. -->
       |<xsl:stylesheet ${xsl(section)}
       |  xmlns:db="${DocBook.Namespace.uri}"
       |  exclude-result-prefixes="db">
       |
       |${section.customStylesheet}
       |</xsl:stylesheet>
       |""".stripMargin

  private def xsl(section: Section): String =
    Namespace.Xsl.withVersion(section.xsltVersion)
}