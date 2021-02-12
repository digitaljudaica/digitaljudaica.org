package org.opentorah.collector

import org.opentorah.xml.{Attribute, Element, Elements, Parsable, Parser, Unparser}

final class WithSource[T](val source: String, val value: T)

object WithSource {

  final class Of[T](elements: Elements[T]) extends Element[WithSource[T]]("withSource") {
    private val valueElement: Elements.Required[T] = elements.required

    override def contentParsable: Parsable[WithSource[T]] = new Parsable[WithSource[T]] {
      override protected def parser: Parser[WithSource[T]] = for {
        source <- WithSource.sourceAttribute()
        value <- valueElement()
      } yield new WithSource[T](
        source,
        value
      )

      override def unparser: Unparser[WithSource[T]] = Unparser.concat[WithSource[T]](
        WithSource.sourceAttribute(_.source),
        valueElement(_.value)
      )
    }
  }

  private val sourceAttribute: Attribute.Required[String] = Attribute("sourceUrl").required
}
