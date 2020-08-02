package org.opentorah.tei

import org.opentorah.xml.{Antiparser, Attribute, Element, Parser}

final case class Editor(
  role: Option[String],
  persName: Option[EntityReference]
)

object Editor extends Element.WithToXml[Editor]("editor") {

  private val roleAttribute: Attribute[String] = Attribute("role")

  override protected def parser: Parser[Editor] = for {
    role <- roleAttribute.optional
    persName <- EntityReference.personParsable.optional
  } yield new Editor(
    role,
    persName
  )

  override protected val antiparser: Antiparser[Editor] = Antiparser(
    attributes = value => Seq(roleAttribute.withValue(value.role)),
    content = value => value.persName.toSeq.map(EntityReference.toXml)
  )
}