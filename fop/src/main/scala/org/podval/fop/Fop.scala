package org.podval.fop

import java.io.{BufferedOutputStream, File, FileOutputStream, OutputStream}

import org.apache.fop.apps.{FOUserAgent, FopFactory}
import org.podval.fop.util.{Logger, TestLogger, Util}
import org.podval.fop.xml.{Saxon, Xml}

object Fop {

  val defaultConfigurationFile: String =
    s"""${Xml.header}
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

  private val dateFormat: java.text.DateFormat = new java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy")

  // To enable mathematics typesetting in Fop.run(), pass in plugin = Some(plugin), where plugin is
  // - for MathJax: new MathJaxFopPlugin(Mathematics.getMathJax(...))
  // - for JEuclid: new JEuclidFopPlugin
  def run(
    saxon: Saxon = Saxon.Saxon6,
    configurationFile: File,
    creationDate: Option[String] = None,
    author: Option[String] = None,
    title: Option[String] = None,
    subject: Option[String] = None,
    keywords: Option[String] = None,
    inputFile: File,
    outputFile: File,
    plugin: Option[FopPlugin] = None,
    logger: Logger = new TestLogger
  ): Unit = {
    logger.debug(
      s"""Fop.run(
         |  configurationFile = $configurationFile,
         |  inputFile = $inputFile,
         |  outputFile = $outputFile,
         |)""".stripMargin
    )

    val fopFactory: FopFactory = FopFactoryFactory.newFactory(configurationFile, inputFile)

    plugin.foreach(_.configure(fopFactory))

    // PDF metadata:
    val foUserAgent: FOUserAgent = fopFactory.newFOUserAgent

    setPdfMetadata(
      foUserAgent,
      creationDate,
      author,
      title,
      subject,
      keywords
    )

    run(
      saxon,
      fopFactory,
      foUserAgent,
      inputFile,
      outputFile,
      logger
    )
  }

  def setPdfMetadata(
    foUserAgent: FOUserAgent,
    creationDate: Option[String],
    author: Option[String],
    title: Option[String],
    subject: Option[String],
    keywords: Option[String]
  ): Unit = {
    foUserAgent.setCreator(Util.applicationString)
    creationDate.foreach { creationDate =>
      val value: java.util.Date = dateFormat.parse(creationDate)
      foUserAgent.setCreationDate(value)
    }

    foUserAgent.setAuthor(author.orNull)
    foUserAgent.setTitle(title.orNull)
    foUserAgent.setSubject(subject.orNull)
    foUserAgent.setKeywords(keywords.orNull)
  }

  def run(
    saxon: Saxon,
    fopFactory: FopFactory,
    foUserAgent: FOUserAgent,
    inputFile: File,
    outputFile: File,
    logger: Logger
  ): Unit = {
    val outputStream: OutputStream = new BufferedOutputStream(new FileOutputStream(outputFile))
    val fop: org.apache.fop.apps.Fop = fopFactory.newFop("application/pdf", foUserAgent, outputStream)

    try {
      saxon.transform(
        inputFile = inputFile,
        defaultHandler = fop.getDefaultHandler,
        logger = logger
      )
    } finally {
      outputStream.close()
    }
  }
}