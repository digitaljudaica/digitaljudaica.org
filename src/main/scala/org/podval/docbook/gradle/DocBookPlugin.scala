package org.podval.docbook.gradle

import org.gradle.api.{Action, DefaultTask, Plugin, Project}
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.{Zip, ZipEntryCompression}
import java.io.File

import org.gradle.api.file.CopySpec

// Properties are annotated with @BeanProperty to make them visible to Gradle.
final class DocBookPlugin extends Plugin[Project] {
  import DocBookPlugin.includeFiles

  def apply(project: Project): Unit = {
    def file(directory: File, name: String, extension: String): File = new File(directory, name + "." + extension)

    // Should get the main sourceSet, but this is only available via JavaConventions...
    // We *are* using it already in the PrepareDocBookDataTask though...
    val sourceRootDirectory: File = new File(project.getProjectDir, "src/main")
    def sourceDirectory(name: String): File = new File(sourceRootDirectory, name)
    def sourceFile(directory: String, name: String, extension: String): File =
      file(sourceDirectory(directory), name, extension)

    val buildRootDirectory: File = project.getBuildDir
    def buildDirectory(name: String): File = new File(buildRootDirectory, name)
    val expandedEpubDirectory: File = buildDirectory("epub")

    val dataDirectory: File = buildDirectory("data")

    val explodeDocBookXslInto: File = buildDirectory("docBookXsl")
    val docBookXslDirectory: File = new File(explodeDocBookXslInto, "docbook")

    val outputRootDirectory: File = buildDirectory("docBook")
    def outputDirectory(outputType: String): File = new File(outputRootDirectory, outputType)

    ConfigurationInitializer.doIt(sourceRootDirectory, project.getLogger)

    val extension: DocBookExtension = project.getExtensions.create("docbook", classOf[DocBookExtension], project)
    extension.inputFileName.set("index")
    extension.dataGeneratorClass.set("")
    val inputFile: Provider[File] = extension.inputFileName.map(sourceFile("docBook", _, "xml"))

    // Download and unpack DocBook XSLT stylesheets
    val docBookXslConfiguration = project.getConfigurations.create("docbookxsl").defaultDependencies(
      _.add(project.getDependencies.create("net.sf.docbook:docbook-xsl:1.79.1:resources@zip")) : Unit
    ).setVisible(false)

    val prepareTask: Copy = project.getTasks.create("docBookPrepare", classOf[Copy])
    prepareTask.setDescription("Prepare DocBook XSLT stylesheets")
    prepareTask.from(project.zipTree(docBookXslConfiguration.getSingleFile))
    prepareTask.into(explodeDocBookXslInto)

    // Generate content that needs to be included in DocBook by executing the generating code.
    val dataTask: PrepareDocBookDataTask = project.getTasks.create("docBookData", classOf[PrepareDocBookDataTask])
    dataTask.setDescription("Generate data for inclusion in DocBook")
    dataTask.dataDirectory.set(dataDirectory)
    dataTask.dataGeneratorClass.set(extension.dataGeneratorClass)
    Option(project.getTasks.findByName("classes")).foreach(dataTask.getDependsOn.add)

    // Copy CSS files replacing substitution tokens with their values.
    val filterCssTask: FilteringCopyTask = project.getTasks.create("docBookCss", classOf[FilteringCopyTask])
    filterCssTask.setDescription("Inject parameters into CSS files")
    filterCssTask.from(sourceDirectory("css"))
    filterCssTask.into(buildDirectory("css"))
    filterCssTask.tokens.set(extension.parameters)

    // Commonality between all SaxonTask instances.
    def setUpSaxonTask(task: SaxonTask, outputType: String): Unit = {
      task.inputFile.set(inputFile)
      task.dataDirectory.set(dataDirectory)
      task.imagesDirectory.set(sourceDirectory("images"))
      task.xslParameters.set(extension.parameters)
      task.xslDirectory.set(docBookXslDirectory)
      task.stylesheetFile.set(sourceFile("xsl", outputType, "xsl"))
      task.getDependsOn.add(prepareTask)
      task.getDependsOn.add(dataTask)
    }

    // Transform DocBook into HTML.
    val htmlXsltTask: SaxonTask = SaxonTask(project, "docBookHtmlXslt")
    setUpSaxonTask(htmlXsltTask, "html")
    htmlXsltTask.setDescription("DocBook -> HTML")
    htmlXsltTask.outputFile.set(file(outputDirectory("html"), "index", "html"))

    // Add CSS and images to the HTML output.
    val htmlTask: Copy = project.getTasks.create("docBookHtml", classOf[Copy])
    htmlTask.setGroup("publishing")
    htmlTask.setDescription("Process DocBook into HTML")
    htmlTask.into(outputDirectory("html"))
    includeFiles(htmlTask, buildRootDirectory, "css/**")
    includeFiles(htmlTask, sourceRootDirectory, "images/**")
    htmlTask.getDependsOn.add(filterCssTask)
    htmlTask.getDependsOn.add(htmlXsltTask)

    // Transform DocBook into XML-FO (temporary result).
    val foTask: SaxonTask = SaxonTask(project, "docBookFoXslt")
    setUpSaxonTask(foTask, "fo")
    foTask.setDescription("DocBook -> XSL-FO")
    foTask.outputFile.set(extension.inputFileName.map[File](file(buildDirectory("fo"), _, "fo")))

    // Process XSL-FO into PDF.
    val pdfTask: FopTask = FopTask(project, name = "docBookPdf")
    pdfTask.setDescription("Process DocBook into PDF")
    pdfTask.setGroup("publishing")
    pdfTask.inputFile.set(foTask.outputFile)
    pdfTask.configurationFile.set(sourceFile("fop", "fop", "xconf"))
    pdfTask.outputFile.set(extension.inputFileName.map[File](file(outputDirectory("pdf"), _, "pdf")))
    pdfTask.getDependsOn.add(foTask)

    // Transform DocBook into (expanded) EPUB (temporary result).
    val epubXsltTask: SaxonTask = SaxonTask(project, "docBookEpubXslt")
    setUpSaxonTask(epubXsltTask, "epub")
    epubXsltTask.setDescription("DocBook -> EPUB")
    epubXsltTask.outputFile.set(extension.inputFileName.map[File](file(expandedEpubDirectory, _, "epub")))

    // Add CSS and images and package the EPUB.
    val epubTask: Zip = project.getTasks.create("docBookEpub", classOf[Zip])
    epubTask.setDescription("Process DocBook into EPUB")
    epubTask.setGroup("publishing")
    epubTask.setPreserveFileTimestamps(true)
    epubTask.setEntryCompression(ZipEntryCompression.STORED)
    epubTask.getArchiveFileName.set(extension.inputFileName.map[String](_ + ".epub"))
    epubTask.getDestinationDirectory.set(outputDirectory("epub"))
    epubTask.from(new File(expandedEpubDirectory, "OEBPS/"))
    includeFiles(epubTask, expandedEpubDirectory, "META-INF/**")
    includeFiles(epubTask, buildRootDirectory, "css/**")
    includeFiles(epubTask, sourceRootDirectory, "images/**")
    epubTask.getDependsOn.add(epubXsltTask)

    val processTask: DefaultTask = project.getTasks.create("docBookProcess", classOf[DefaultTask])
    processTask.setDescription("Process DocBook into HTML, PDF and EPUB")
    processTask.setGroup("publishing")
    processTask.getDependsOn.add(htmlTask)
    processTask.getDependsOn.add(pdfTask)
    processTask.getDependsOn.add(epubTask)
  }
}

object DocBookPlugin {
  def includeFiles(task: CopySpec, from: File, pattern: String): Unit = {
    task.from(from.asInstanceOf[Any], new Action[CopySpec] {
      override def execute(copySpec: CopySpec): Unit = {
        copySpec.include(pattern)
      }
    })
  }
}
