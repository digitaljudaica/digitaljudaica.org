package org.digitaljudaica.archive.collector

import scala.xml.Elem
import Xml.Ops

final class Name(
  entity: Entity,
  xml: Elem
) {
  xml.check(entity.nameElement)

  val name: String = xml.text

  val id: Option[String] = xml.attributeOption("xml:id")

  def toXml: Elem = {
    <name xml:id={id.orNull}>{name}</name>
      .copy(label = entity.nameElement)
  }
}
