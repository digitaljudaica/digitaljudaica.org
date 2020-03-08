package org.opentorah.xml

import zio.ZIO
import scala.xml.Elem

class ElementRaw[A](
  elementName: String,
  fromXml: Elem => A
) extends Parsable[A] {
  override def toString: String = s"raw element $elementName"

  final override def optional: Parser[Option[A]] = for {
    hasNext <- Xml.nextNameIs(elementName)
    result <- if (!hasNext) ZIO.none else Xml.nextElement.map(_.map(fromXml))
  } yield result
}

object ElementRaw {

  def apply(elementName: String): Parsable[Elem] =
    new ElementRaw(elementName, identity)

  def apply[A](elementName: String, fromXml: Elem => A): Parsable[A] =
    new ElementRaw(elementName, fromXml)
}