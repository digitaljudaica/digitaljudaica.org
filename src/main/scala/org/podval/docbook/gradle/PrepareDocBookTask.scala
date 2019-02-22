package org.podval.docbook.gradle

import org.gradle.api.artifacts.{Configuration, Dependency}
import org.gradle.api.DefaultTask
import org.gradle.api.file.{CopySpec, FileCopyDetails, RelativePath}
import org.gradle.api.provider.{ListProperty, MapProperty, Property}
import org.gradle.api.tasks.{Input, TaskAction}
import java.io.File
import scala.beans.BeanProperty
import scala.collection.JavaConverters._
import Util.writeInto

class PrepareDocBookTask extends DefaultTask  {

  private val layout: Layout = Layout.forProject(getProject)

  private val logger: Logger = new Logger.PluginLogger(getLogger)

  @BeanProperty val xslt1version: Property[String] =
    getProject.getObjects.property(classOf[String])

  @BeanProperty val xslt2version: Property[String] =
    getProject.getObjects.property(classOf[String])

  @Input @BeanProperty val document: Property[String] =
    getProject.getObjects.property(classOf[String])

  @BeanProperty val parameters: MapProperty[String, java.util.Map[String, String]] =
    getProject.getObjects.mapProperty(classOf[String], classOf[java.util.Map[String, String]])

  @Input @BeanProperty val substitutions: MapProperty[String, String] =
    getProject.getObjects.mapProperty(classOf[String], classOf[String])

  @Input @BeanProperty val cssFile: Property[String] =
    getProject.getObjects.property(classOf[String])

  @Input @BeanProperty val epubEmbeddedFonts: ListProperty[String] =
    getProject.getObjects.listProperty(classOf[String])

  @TaskAction
  def prepareDocBook(): Unit = {
    val documentName: String = Util.dropAllowedExtension(document.get, "xml")
    val cssFileName: String = Util.dropAllowedExtension(cssFile.get, "css")

    // Verify parameter sections
    val allParameters: Map[String, Map[String, String]] =
      parameters.get.asScala.toMap.mapValues(_.asScala.toMap)

    val unclaimedParameterSections: Set[String] = Util.unclaimedParameterSections(allParameters, DocBook2.processors.toSet)
    if (unclaimedParameterSections.nonEmpty) {
      val sections: String = DocBook2.processors.map { processor =>
        "  " + processor.name + ": " + processor.parameterSections.mkString(", ")
      }.mkString("\n")

      throw new IllegalArgumentException(
        s"""Unsupported parameter sections: ${unclaimedParameterSections.mkString(", ")}.
           |Supported sections are:
           |$sections
           |""".stripMargin
      )
    }

    // XSLT stylesheets
    unpackDocBookXsl(Stylesheets.xslt1, xslt1version.get)
    unpackDocBookXsl(Stylesheets.xslt2, xslt2version.get)

    // Input file
    writeInto(layout.inputFile(documentName), logger, replace = false) {
      """<?xml version="1.0" encoding="UTF-8"?>
        |<!DOCTYPE article
        |  PUBLIC "-//OASIS//DTD DocBook XML V5.0//EN"
        |  "http://www.oasis-open.org/docbook/xml/5.0/dtd/docbook.dtd">
        |
        |<article xmlns="http://docbook.org/ns/docbook" version="5.0"
        |         xmlns:xi="http://www.w3.org/2001/XInclude">
        |</article>
        |"""
    }

    // CSS
    writeInto(layout.cssFile(cssFileName), logger, replace = false) {
      """@namespace xml "http://www.w3.org/XML/1998/namespace";
        |"""
    }

    // FOP configuration
    writeInto(layout.fopConfigurationFile, logger, replace = false) {
      s"""<?xml version="1.0" encoding="UTF-8"?>
         |<fop version="1.0">
         |  <renderers>
         |    <renderer mime="application/pdf">
         |      <fonts>
         |        <!-- FOP will detect fonts available in the operating system. -->
         |        <auto-detect/>
         |      </fonts>
         |    </renderer>
         |  </renderers>
         |</fop>
         |"""
    }

    // substitutions DTD
    writeInto(layout.xmlFile(layout.substitutionsDtdFileName), logger, replace = true) {
      substitutions.get.asScala.toSeq.map {
        case (name: String, value: String) => s"""<!ENTITY $name "$value">\n"""
      }.mkString
    }

    // XML catalog
    writeInto(layout.catalogFile, logger, replace = true) {
      val data: String = layout.dataDirectoryRelative

      s"""<?xml version="1.0" encoding="UTF-8"?>
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
         |  <public publicId="-//OASIS//DTD DocBook XML V5.0//EN"
         |          uri="${layout.substitutionsDtdFileName}"/>
         |
         |  <nextCatalog catalog="${layout.catalogCustomFileName}"/>
         |</catalog>
         |"""
    }

    // XML catalog customization
    writeInto(layout.xmlFile(layout.catalogCustomFileName), logger, replace = false) {
      s"""<?xml version="1.0" encoding="UTF-8"?>
         |<!DOCTYPE catalog
         |  PUBLIC "-//OASIS//DTD XML Catalogs V1.1//EN"
         |  "http://www.oasis-open.org/committees/entity/release/1.1/catalog.dtd">
         |
         |<!-- Customizations go here. -->
         |<catalog xmlns="urn:oasis:names:tc:entity:xmlns:xml:catalog" prefer="public">
         |  <nextCatalog catalog="/etc/xml/catalog"/>
         |</catalog>
         |"""
    }

    // Stylesheet files and customizations

    val epubEmbeddedFontsStr: String = {
      val names: List[String] = epubEmbeddedFonts.get.asScala.toList
      if (names.isEmpty) "" else Fop.getFontFiles(layout.fopConfigurationFile, names, logger)
    }

    DocBook2.processors.foreach { docBook2: DocBook2 =>
      writeStylesheetFiles(
        docBook2 = docBook2,
        layout = layout,
        documentName = documentName,
        cssFileName = cssFileName,
        epubEmbeddedFonts = epubEmbeddedFontsStr,
        parameters = docBook2.parameterSections.flatMap(allParameters.get).flatten.toMap
      )
    }
  }

  private def unpackDocBookXsl(stylesheets: Stylesheets, version: String): Unit = {
    val directory: File = layout.docBookXslDirectory(stylesheets.directoryName)
    if (!directory.exists) {
      val dependencyNotation: String = stylesheets.dependencyNotation(version)
      logger.info(s"Retrieving DocBook ${stylesheets.name} stylesheets: $dependencyNotation")
      val file = getArtifact(stylesheets, dependencyNotation)
      logger.info(s"Unpacking ${file.getName}")
      unpack(
        zipFile = file,
        archiveSubdirectoryName = stylesheets.archiveSubdirectoryName,
        directory = directory
      )
    }
  }

  private def getArtifact(stylesheets: Stylesheets, dependencyNotation: String): File = {
    val dependency: Dependency = getProject.getDependencies.create(dependencyNotation)
    val configuration: Configuration = getProject.getConfigurations.detachedConfiguration(dependency)
    configuration.setTransitive(false)
    configuration.getSingleFile
  }

  private def unpack(zipFile: File, archiveSubdirectoryName: String, directory: File): Unit = {
    val toDrop: Int = archiveSubdirectoryName.count(_ == '/') + 1
    getProject.copy((copySpec: CopySpec) => copySpec
      .into(directory)
      .from(getProject.zipTree(zipFile))
      // following code deals with extracting just the "docbook" directory;
      // this should become easier in Gradle 5.3, see:
      // https://github.com/gradle/gradle/issues/1108
      // https://github.com/gradle/gradle/pull/5405
      .include(archiveSubdirectoryName + "/**")
      .eachFile((file: FileCopyDetails) =>
        file.setRelativePath(new RelativePath(true, file.getRelativePath.getSegments.drop(toDrop): _*))
      )
      .setIncludeEmptyDirs(false))
  }

  private def writeStylesheetFiles(
    docBook2: DocBook2,
    layout: Layout,
    documentName: String,
    cssFileName: String,
    epubEmbeddedFonts: String,
    parameters: Map[String, String]
  ): Unit = {
    def parameterIf(condition: Boolean, name: String, value: String): Option[(String, String)] =
      if (!condition) None else Some(name -> value)

    val defaultParameters: Map[String, String] = Seq[Option[(String, String)]](
      Some("img.src.path", layout.imagesDirectoryName + "/"),
      parameterIf(docBook2.usesHtml && !docBook2.usesDocBookXslt2 && !logger.isInfoEnabled,
        "chunk.quietly", "1"),
      parameterIf(docBook2.usesHtml,
        "base.dir", layout.baseDir(docBook2)),
      parameterIf(docBook2.usesHtml,
        "root.filename", layout.rootFilename(docBook2, documentName)),
      parameterIf(docBook2.isEpub,
        "epub.embedded.fonts", epubEmbeddedFonts),
      parameterIf(docBook2.usesCss,
        if (docBook2.usesDocBookXslt2) "html.stylesheets" else "html.stylesheet",
        layout.cssFileRelativeToOutputDirectory(cssFileName))
    ).flatten.toMap

    val stylesheetName: String = docBook2.stylesheetName
    val customStylesheetName: String = layout.customStylesheet(stylesheetName)
    val paramsStylesheetName: String = layout.paramsStylesheet(stylesheetName)
    val stylesheetUri: String = s"${Stylesheets(docBook2.usesDocBookXslt2).uri}/${docBook2.stylesheetUriName}.xsl"

    // xsl:param has the last value assigned to it, so customization must come last;
    // since it is imported (so as not to be overwritten), and import elements must come first,
    // a separate "-param" file is written with the "default" values for the parameters :)

    writeInto(layout.stylesheetFile(layout.mainStylesheet(stylesheetName)), logger, replace = true) {
      s"""<?xml version="1.0" encoding="UTF-8"?>
         |<!-- DO NOT EDIT! Generated by the DocBook plugin.
         |     Customizations go into $customStylesheetName. -->
         |<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
         |  <xsl:import href="$stylesheetUri"/>
         |  <xsl:import href="$paramsStylesheetName"/>
         |  <xsl:import href="$customStylesheetName"/>
         |</xsl:stylesheet>
         |"""
    }

    writeInto(layout.stylesheetFile(paramsStylesheetName), logger, replace = true) {
      val parametersStr: String = (defaultParameters ++ parameters).map { case (name: String, value: String) =>
        s"""  <xsl:param name="$name">$value</xsl:param>"""
      }.mkString("\n")

      s"""<?xml version="1.0" encoding="UTF-8"?>
         |<!-- DO NOT EDIT! Generated by the DocBook plugin.
         |     Customizations go into $customStylesheetName. -->
         |<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
         |$parametersStr
         |</xsl:stylesheet>
         |"""
    }

    writeInto(layout.stylesheetFile(customStylesheetName), logger, replace = false) {
      s"""<?xml version="1.0" encoding="UTF-8"?>
         |<!-- Customizations go here. -->
         |<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
         |</xsl:stylesheet>
         |"""
    }
  }
}
