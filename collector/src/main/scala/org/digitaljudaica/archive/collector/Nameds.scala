package org.digitaljudaica.archive.collector

import java.io.File
import scala.xml.Elem
import Xml.Ops

final case class Nameds(
  name: String,
  head: String,
  nameds: Seq[Named],
  entity: Entity
) {
  def references: Seq[Name] = nameds.flatMap(_.references)

  def addMentions(references: Seq[Name]): Nameds =
    copy(nameds = nameds.map(_.addMentions(references)))

  def toXml: Elem =
    <list xml:id={name}>
      <head>{head}</head>
      {for (named <- nameds) yield named.toXml}
    </list>
    .copy(label = entity.listElement)
}

object Nameds {
  def parse(document: DocumentLike, xml: Elem, directory: File, errors: Errors): Nameds = {
    val entity: Entity = Entity.forList(xml.label).get
    val name: String = xml.getAttribute("xml:id")
    val head: String = xml.oneChild("head").text
    val listDirectory: File = new File(directory, name)
    val nameds: Seq[Named] =
      for (fileName <- Util.filesWithExtensions(listDirectory, ".xml").sorted)
      yield Named.parse(entity, document, listDirectory, fileName, errors)

    Nameds(
      name = name,
      head = head,
      nameds = nameds,
      entity
    )
  }
}
