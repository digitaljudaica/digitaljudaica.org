package org.opentorah.collector

import org.opentorah.tei.{EntitiesList, EntityName, Ref}
import org.opentorah.util.Files
import scala.xml.{Elem, Node}

final class NamesObject(site: Site) extends SimpleSiteObject(site) {

  override protected def fileName: String = NamesObject.fileName

  override protected def teiWrapperViewer: Viewer = Viewer.Names

  override protected def teiBody: Seq[Node] = {
    val nonEmptyLists: Seq[EntitiesList] = site.store.entities.get.lists.filterNot(_.isEmpty)
    val listOfLists: Seq[Node] =
      <p>{for (list <- nonEmptyLists) yield <l>{Ref.toXml(NamesObject.entityInTheListUrl(list.id), list.head)}</l>}</p>

    def toXml(value: EntitiesList): Elem =
      <list xml:id={value.id} role={value.role.orNull}>
        <head>{value.head}</head>
        {for (entity <- value.entities) yield {
        <l>{Ref.toXml(EntityObject.teiWrapperUrl(entity), EntityName.toXmlElement(entity.names.head))}</l>
      }}
      </list>
        .copy(label = value.entityType.listElement)

    <head>{NamesObject.title}</head> ++ listOfLists ++ nonEmptyLists.flatMap(toXml)
  }

  override protected def yaml: Seq[(String, String)] = Seq("title" -> NamesObject.title)
}

object NamesObject {

  val fileName: String = "names"

  val title: String = "Имена"

  def entityInTheListUrl(id: String): Seq[String] = Files.addPart(Seq(fileName + ".html"), id)
}
