package org.opentorah.collector

import org.opentorah.metadata.Names
import org.opentorah.html
import org.opentorah.site.{Caching, ListFile, Site => HtmlSite}
import org.opentorah.tei.{Availability, CalendarDesc, EntityReference, EntityType, LangUsage, Language, LinksResolver,
  ProfileDesc, PublicationStmt, Publisher, SourceDesc, Tei, Unclear, Entity => TeiEntity}
import org.opentorah.util.{Effects, Files}
import org.opentorah.xml.{Attribute, Element, FromUrl, Parsable, Parser, PrettyPrinter, Unparser, Xml}
import org.slf4j.{Logger, LoggerFactory}
import zio.{App, ExitCode, Task, UIO, URIO, ZEnv, ZIO}
import java.io.File
import java.net.URL

// TODO retrieve TEI(?) references from notes.
final class Site(
  override val fromUrl: FromUrl,
  override val names: Names,
  title: HtmlSite.Title.Value,
  siteUrl: String,
  val facsimilesUrl: String,
  favicon: String,
  val sourceDesc: SourceDesc.Value,
  val calendarDesc: CalendarDesc.Value,
  pages: Seq[String],
  licenseName: String,
  licenseUrl: String,
  googleAnalyticsId: Option[String],
  email: String,
  githubUsername: Option[String],
  twitterUsername: Option[String],
  footer: HtmlSite.Footer.Value,
  val entities: Entities,
  val entityLists: EntityLists,
  val notes: Notes,
  val by: ByHierarchy
) extends HtmlSite[Site](
  title,
  siteUrl,
  favicon,
  pages,
  licenseName,
  licenseUrl,
  googleAnalyticsId,
  isMathJaxEnabled = false,
  email,
  githubUsername,
  twitterUsername,
  footer,
  Viewer.default
) with Store with FromUrl.With {

  override protected def resolveNavigationalLink(url: String): Caching.Parser[Xml.Element] = resolve(url).map { pathOpt =>
    val path: Store.Path = pathOpt.get
    a(path)(text = path.last.asInstanceOf[HtmlContent].htmlHeadTitle.getOrElse("NO TITLE"))
  }

  override protected def resolveHtmlContent(htmlContent: org.opentorah.site.HtmlContent[Site]): Caching.Parser[Xml.Element] =
    htmlContent.content(this) >>= (content => Tei.toHtml(
      linkResolver(htmlContent match {
        case htmlFacet: Document.TextFacet => Some (htmlFacet)
        case _ => None
      }),
      content
    ))

  override protected def htmlPrettyPrinter: PrettyPrinter = Site.htmlPrettyPrinter

  private val paths: Seq[Store.Path] = getPaths(Seq.empty, by)

  private def getPaths(path: Store.Path, by: ByHierarchy): Seq[Store.Path] = by.stores.flatMap { store =>
    val storePath: Store.Path = path ++ Seq(by, store)
    Seq(storePath) ++ (store match {
      case hierarchy : Hierarchy  => getPaths(storePath, hierarchy.by)
      case _ => Seq.empty
    })
  }

  val store2path: Map[Store, Store.Path] = paths.map(path => path.last -> path).toMap

  val collections: Seq[Collection] = for {
    path <- paths
    last = path.last
    if last.isInstanceOf[Collection]
  } yield last.asInstanceOf[Collection]

  val hierarchies: Seq[Hierarchy] = for {
    path <- paths
    last = path.last
    if last.isInstanceOf[Hierarchy]
  } yield last.asInstanceOf[Hierarchy]

  val alias2collectionAlias: Map[String, Collection.Alias] = collections
    .filter(_.alias.isDefined)
    .map(collection => collection.alias.get -> new Collection.Alias(collection))
    .toMap

  private val references: ListFile[WithSource[EntityReference], Seq[WithSource[EntityReference]]] = WithSource(
    url = Files.fileInDirectory(fromUrl.url, "references-generated.xml"),
    name = "references",
    value = EntityReference
  )
  def getReferences: Caching.Parser[Seq[WithSource[EntityReference]]] = references.get

  private val unclears: ListFile[WithSource[Unclear.Value], Seq[WithSource[Unclear.Value]]] = WithSource(
    url = Files.fileInDirectory(fromUrl.url, "unclears-generated.xml"),
    name = "unclears",
    value = Unclear.element
  )
  def getUnclears: Caching.Parser[Seq[WithSource[Unclear.Value]]] = unclears.get

  def resolve(url: String): Caching.Parser[Option[Store.Path]] = resolve(Files.splitAndDecodeUrl(url))

  def resolve(path: Seq[String]): Caching.Parser[Option[Store.Path]] =
    if (path.isEmpty) ZIO.some(Seq(Index.Flat))
    else Store.resolve(path, this, Seq.empty)

  // TODO ZIOify logging!
  //info(request, storePath.fold(s"--- ${Files.mkUrl(path)}")(storePath => s"YES ${storePath.mkString("")}"))
  def resolveContent(path: Seq[String]): Task[Option[(String, Boolean)]] =
    toTask(resolve(path) >>= (_.map(content(_).map(Some(_))).getOrElse(ZIO.none)))

  private def content(path: Store.Path): Caching.Parser[(String, Boolean)] = path.lastOption.getOrElse(this) match {
    case teiFacet   : Document.TeiFacet => renderTeiContent (teiFacet   ).map((_, true ))
    case htmlContent: HtmlContent       => renderHtmlContent(htmlContent).map((_, false))
  }

  private def renderTeiContent(teiFacet: Document.TeiFacet): Caching.Parser[String] = teiFacet.getTei.map(tei =>
    Tei.renderXml(tei.copy(teiHeader = tei.teiHeader.copy(
      fileDesc = tei.teiHeader.fileDesc.copy(
        publicationStmt = Some(new PublicationStmt(
          publisher = Some(new Publisher.Value(<ptr target={s"http://$siteUrl"}/>)),
          availability = Some(new Availability(
            status = Some("free"),
            xml = <licence><ab><ref n="license" target={licenseUrl}>{licenseName}</ref></ab></licence>
          ))
        )),
        sourceDesc = Some(sourceDesc)
      ),
      profileDesc = Some(tei.teiHeader.profileDesc.getOrElse(ProfileDesc.empty).copy(
        langUsage = Some(LangUsage(languages = Seq(Language(
          ident = tei.text.lang.get,
          usage = None,
          text = None
        )))),
        calendarDesc = Some(calendarDesc)
      ))
    )))
  )

  def a(htmlContent: HtmlContent): html.a = a(htmlContent.path(this))

  def a(path: Store.Path): html.a = html
    .a(path.map(_.structureName))
    .setTarget((path.last match {
      case htmlContent: HtmlContent => htmlContent.viewer
      case _ => Viewer.default
    }).name)

  override def findByName(name: String): Caching.Parser[Option[Store]] =
    ZIO.succeed(alias2collectionAlias.get(name)) >>= {
      case Some(result) => ZIO.some(result)
      case None =>
        Store.findByName(name, Seq(entities, notes, Reports, by)) >>= {
          case Some(result) => ZIO.some(result)
          case None => Store.findByName(
            name,
            "html",
            name => Store.findByName(name, Seq(Index.Flat, Index.Tree, entityLists))
          )
        }
    }

  private def linkResolver(textFacet: Option[Document.TextFacet]): LinksResolver = new LinksResolver {
    private val facsUrl: Option[Store.Path] = textFacet.map(textFacet =>
      textFacet.collection.facsimileFacet.of(textFacet.document).path(Site.this))

    def toUIO(parser: Caching.Parser[Option[html.a]], error: => String): UIO[Option[html.a]] =
      toTask(parser.map { a =>
        if (a.isEmpty) Site.logger.warn(error)
        a
      }).orDie

    override def resolve(url: Seq[String]): UIO[Option[html.a]] = toUIO(
      Site.this.resolve(url).map(_.map(path => a(path))),
      s"did not resolve: $url"
    )

    override def findByRef(ref: String): UIO[Option[html.a]] = toUIO(
      entities.findByName(ref).map(_.map(entity => entity.a(Site.this))),
      s"did not find reference: $ref"
    )

    override def facs(pageId: String): UIO[Option[html.a]] = toUIO(
      ZIO.succeed(facsUrl.map(facsUrl => a(facsUrl).setFragment(pageId))),
      "did not get facsimile: $pageId"
    )
  }

  def build(withPrettyPrint: Boolean): Task[Unit] = toTask {
    caching.logEnabled = false

    for {
      _ <- Effects.effect(Site.logger.info("Writing site lists."))
      _ <- entities.writeDirectory()
      _ <- notes.writeDirectory()
      // Collection lists must exist by the time we gather references and such:
      _ <- ZIO.foreach_(collections)(_.writeDirectory())

      _ <- Effects.effect(Site.logger.info("Writing references."))
      allReferences <- WithSource.all[EntityReference](
        this,
        nodes => ZIO.foreach(EntityType.values)(entityType =>
          Xml.descendants(nodes, entityType.nameElement, EntityReference)).map(_.flatten)
      )
      _ <- Effects.effect(references.write(allReferences))

      _ <- Effects.effect(Site.logger.info("Writing unclears."))
      allUnclears <- WithSource.all[Unclear.Value](
        this,
        nodes => Xml.descendants(nodes, Unclear.element.elementName, Unclear.element)
      )
      _ <- Effects.effect(unclears.write(allUnclears))

      _ <- Effects.effect(Site.logger.info("Verifying site."))

      errorOpts <- getReferences >>= (ZIO.foreach(_) { value =>
        val reference: EntityReference = value.value
        val name: Xml.Nodes = reference.name
        reference.ref.fold[Caching.Parser[Option[String]]](ZIO.none)(ref =>
          if (ref.contains(" ")) ZIO.some(s"""Value of the ref attribute contains spaces: ref="$ref" """)
          else entities.findByName(ref).map(_
            .fold[Option[String]](Some(s"""Unresolvable reference: Name ref="$ref">${name.text}< """))(named =>
              if (named.entityType == reference.entityType) None
              else Some(s"${reference.entityType} reference to ${named.entityType} ${named.name}: $name [$ref]")
            )
          )
        )
      })
      errors = errorOpts.flatten
      _ <- Effects.check(errors.isEmpty, errors.mkString("\n"))

      // detect and log unresolved references
      allNotes <- notes.directoryEntries
      allEntities <- entities.directoryEntries
      _ <- ZIO.foreach_(hierarchies ++ collections ++ allNotes ++ allEntities)(resolveHtmlContent)

      _ <- ZIO.foreach_(collections)(collection => collection.directoryEntries >>= (ZIO.foreach_(_)(document =>
          resolveHtmlContent(collection.textFacet.of(document)))))

      _ <- if (!withPrettyPrint) Effects.ok else prettyPrint()
    } yield ()
  }

  private def prettyPrint(): Caching.Parser[Unit] = for {
    _ <- Effects.effect(Site.logger.info("Pretty-printing site."))

    _ <- entities.directoryEntries >>= (ZIO.foreach_(_)(entity => entities.getFile(entity).map(teiEntity =>
      entities.writeFile(
        entity,
        content = Store.renderXml(TeiEntity.xmlElement(teiEntity))
      )
    )))

    _ <- ZIO.foreach_(collections)(collection => ZIO.succeed(Files.write(
      file = Files.url2file(collection.fromUrl.url),
      content = Store.renderXml(Collection, collection)
    )))

    _ <- ZIO.foreach_(collections)(collection =>
      collection.directoryEntries >>= (ZIO.foreach_(_)(document => collection.getFile(document).map(text =>
        collection.writeFile(
        document,
        content = Tei.renderXml(text)
      ))))
    )
  } yield ()

  private def toTask[T](parser: Caching.Parser[T]): Task[T] = Parser.toTask(Caching.provide(caching, parser))
}

object Site extends Element[Site]("site") with App {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val htmlPrettyPrinter: PrettyPrinter = Tei.prettyPrinter.copy(
    alwaysStackElements = Tei.prettyPrinter.alwaysStackElements ++ Set("nav", "header", "main", "div", "store"),
    // Note: only the ones derived from TEI notes need to cling, but:
    clingyElements = Tei.prettyPrinter.clingyElements ++ Set("a"),
    allowEmptyElements = false,
    // ... except, some elements are mis-processed when they *are* non-empty (e.g., <br>),
    // and in general, it's weird to expand the elements that are always empty:
    keepEmptyElements = Set("br", "meta", "link", "img", "data"),
    preformattedElements = Set("pre")
  )

  def read(rootUrl: URL): Task[Site] = Parser.toTask {
    Effects.effect(logger.info(s"Reading site from $rootUrl")) *>
    Site.parse(Files.fileInDirectory(rootUrl, "site.xml"))
  }

  override def run(args: List[String]): URIO[ZEnv, ExitCode] = {
    val withPrettyPrint: Boolean = args.length > 1 && (args(1) == "prettyPrint")
    Site.read(Files.file2url(new File(args.head))) >>= (_.build(withPrettyPrint))
  }.exitCode

  private val facsimilesUrlAttribute: Attribute.Required[String] = Attribute("facsimilesUrl").required

  override def contentParsable: Parsable[Site] = new Parsable[Site] {
    override def parser: Parser[Site] = for {
      fromUrl <- Element.currentFromUrl
      names <- Names.withDefaultNameParsable()
      title <- HtmlSite.Title.element.required()
      siteUrl <- HtmlSite.siteUrlAttribute()
      facsimilesUrl <- facsimilesUrlAttribute()
      favicon <- HtmlSite.faviconAttribute()
      sourceDesc <- SourceDesc.element.required()
      calendarDesc <- CalendarDesc.element.required()
      pages <- HtmlSite.Page.seq()
      licenseName <- HtmlSite.licenseNameAttribute()
      licenseUrl <- HtmlSite.licenseUrlAttribute()
      googleAnalyticsId <- HtmlSite.googleAnalyticsIdAttribute()
      email <- HtmlSite.emailAttribute()
      githubUsername <- HtmlSite.githubUsernameAttribute()
      twitterUsername <- HtmlSite.twitterUsernameAttribute()
      footer <- HtmlSite.Footer.element.required()
      entities <- Entities.required()
      entityLists <- EntityLists.required()
      notes <- Notes.required()
      by <- ByHierarchy.followRedirects.required()
    } yield new Site(
      fromUrl,
      names,
      title,
      siteUrl,
      facsimilesUrl,
      favicon,
      sourceDesc,
      calendarDesc,
      pages,
      licenseName,
      licenseUrl,
      googleAnalyticsId,
      email,
      githubUsername,
      twitterUsername,
      footer,
      entities,
      entityLists,
      notes,
      by
    )

    override def unparser: Unparser[Site] = Unparser.concat[Site](
      Names.withDefaultNameParsable(_.names),
      HtmlSite.Title.element.required(_.title),
      HtmlSite.siteUrlAttribute(_.siteUrl),
      facsimilesUrlAttribute(_.facsimilesUrl),
      HtmlSite.faviconAttribute(_.favicon),
      SourceDesc.element.required(_.sourceDesc),
      CalendarDesc.element.required(_.calendarDesc),
      HtmlSite.Page.seq(_.pages),
      HtmlSite.licenseNameAttribute(_.licenseName),
      HtmlSite.licenseUrlAttribute(_.licenseUrl),
      HtmlSite.googleAnalyticsIdAttribute(_.googleAnalyticsId),
      HtmlSite.emailAttribute(_.email),
      HtmlSite.githubUsernameAttribute(_.githubUsername),
      HtmlSite.twitterUsernameAttribute(_.twitterUsername),
      HtmlSite.Footer.element.required(_.footer),
      Entities.required(_.entities),
      EntityLists.required(_.entityLists),
      Notes.required(_.notes),
      ByHierarchy.required(_.by)
    )
  }
}
