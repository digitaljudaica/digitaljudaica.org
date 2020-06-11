package org.opentorah.xml

import java.io.{File, FileWriter, StringReader}
import javax.xml.transform.dom.DOMResult
import javax.xml.transform.sax.{SAXResult, SAXSource, SAXTransformerFactory}
import javax.xml.transform.stream.{StreamResult, StreamSource}
import javax.xml.transform.{ErrorListener, Result => TransformResult, Source, Transformer, TransformerException}
import org.slf4j.{Logger, LoggerFactory}
import org.w3c.dom.Node
import org.xml.sax.helpers.DefaultHandler
import org.xml.sax.{ErrorHandler, InputSource, SAXParseException, XMLReader}

sealed abstract class Saxon(name: String) {

  final override def toString: String = name

  protected final def getTransformerFactory: SAXTransformerFactory = {
    val result: SAXTransformerFactory = newTransformerFactory

    // To process DocBook stylesheets (see also Svg.scala), Saxon needs real Xerces parser,
    // not the one included in the JDK or included with Saxon (com.icl.saxon.aelfred.SAXParserFactoryImpl).
    // Classpath-based discovery is unstable (order changes from one Gradle version to another) and ugly.
    // Tell Saxon to use Xerces parser explicitly:
    result.setAttribute(styleParserClassAttribute, Xerces.saxParserName)
    result.setAttribute(sourceParserClassAttribute, Xerces.saxParserName)

    result
  }

  protected def newTransformerFactory: SAXTransformerFactory

  protected def styleParserClassAttribute: String

  protected def sourceParserClassAttribute: String

  def transform(
    resolver: Resolver,
    inputFile: File,
    stylesheetFile: File,
    xmlReader: XMLReader,
    outputFile: Option[File]
  ): Unit = transform(
    resolver = Some(resolver),
    stylesheetFile = Some(stylesheetFile),
    inputSource = new InputSource(inputFile.toURI.toASCIIString),
    xmlReader,
    result = getOutputTarget(outputFile)
  )

  private def getOutputTarget(outputFile: Option[File]): TransformResult = {
    val result = new StreamResult
    outputFile.map { outputFile =>
      result.setSystemId(outputFile)
      result.setWriter(new FileWriter(outputFile))
      result
    }.getOrElse {
      result.setSystemId("dev-null")
      result.setOutputStream((_: Int) => {})
      result
    }
  }

  def transform(
    inputFile: File,
    defaultHandler: DefaultHandler
  ): Unit = transform(
    resolver = None,
    stylesheetFile = None,
    source = new StreamSource(inputFile),
    result = new SAXResult(defaultHandler)
  )

  def parse(
    input: String,
    xmlReader: XMLReader
  ): Node = {
    val result = new DOMResult

    transform(
      resolver = None,
      stylesheetFile = None,
      inputSource = new InputSource(new StringReader(input)),
      xmlReader,
      result
    )

    result.getNode
  }

  private def transform(
    resolver: Option[Resolver],
    stylesheetFile: Option[File],
    inputSource: InputSource,
    xmlReader: XMLReader,
    result: TransformResult
  ): Unit = {
    resolver.foreach(resolver => xmlReader.setEntityResolver(resolver))
    Saxon.setErrorHandler(xmlReader, Saxon.logger)

    transform(
      resolver,
      stylesheetFile,
      source = new SAXSource(xmlReader, inputSource),
      result
    )
  }

  private def transform(
    resolver: Option[Resolver],
    stylesheetFile: Option[File],
    source: Source,
    result: TransformResult
  ): Unit = {
    Saxon.logger.debug(
      s"""Saxon.transform(
         |  saxon = $this,
         |  stylesheetFile = $stylesheetFile,
         |  source = ${source.getSystemId},
         |  result = ${result.getSystemId}
         |)""".stripMargin
    )

    val transformerFactory: SAXTransformerFactory = getTransformerFactory

    // Note: To intercept all network requests, URIResolver has to be set on the transformerFactory,
    // not the transformer itself: I guess some sub-transformers get created internally ;)
    resolver.foreach(resolver => transformerFactory.setURIResolver(resolver))

    Saxon.setErrorListener(transformerFactory, Saxon.logger)

    val transformer: Transformer = stylesheetFile.fold(transformerFactory.newTransformer) {
      stylesheetFile => transformerFactory.newTransformer(new StreamSource(stylesheetFile))
    }

    transformer.transform(source, result)
  }
}

object Saxon {

  private val logger: Logger = LoggerFactory.getLogger(classOf[Saxon])

  object Saxon6 extends Saxon("Saxon 6") {
    override protected def newTransformerFactory: SAXTransformerFactory = new com.icl.saxon.TransformerFactoryImpl
    override protected def styleParserClassAttribute: String = com.icl.saxon.FeatureKeys.STYLE_PARSER_CLASS
    override protected def sourceParserClassAttribute: String = com.icl.saxon.FeatureKeys.SOURCE_PARSER_CLASS
  }

  // Saxon6 produces unmodifiable DOM, which can not be serialized; Saxon 10's DOM can.
  object Saxon10 extends Saxon("Saxon 10") {
    override protected def newTransformerFactory: SAXTransformerFactory = new net.sf.saxon.TransformerFactoryImpl
    override protected def styleParserClassAttribute: String = net.sf.saxon.lib.FeatureKeys.STYLE_PARSER_CLASS
    override protected def sourceParserClassAttribute: String = net.sf.saxon.lib.FeatureKeys.SOURCE_PARSER_CLASS
  }

  def setErrorHandler(xmlReader: XMLReader, logger: Logger): Unit =
    xmlReader.setErrorHandler(new ErrorHandler {
      override def warning(exception: SAXParseException): Unit = logger.warn(exception.toString)
      override def error(exception: SAXParseException): Unit = logger.error(exception.toString)
      override def fatalError(exception: SAXParseException): Unit = logger.error(exception.toString)
    })

  def setErrorListener(transformerFactory: SAXTransformerFactory, logger: Logger): Unit =
    transformerFactory.setErrorListener(new ErrorListener {
      override def warning(exception: TransformerException): Unit = logger.warn(exception.getMessageAndLocation)
      override def error(exception: TransformerException): Unit = logger.error(exception.getMessageAndLocation)
      override def fatalError(exception: TransformerException): Unit = logger.error(exception.getMessageAndLocation)
    })
}
