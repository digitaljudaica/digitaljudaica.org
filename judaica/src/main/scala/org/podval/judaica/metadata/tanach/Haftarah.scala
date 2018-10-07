package org.podval.judaica.metadata.tanach

import org.podval.judaica.metadata.{LanguageSpec, Language, Metadata, XML}
import org.podval.judaica.metadata.tanach.SpanParser.{NumberedProphetSpan, ProphetSpanParsed}

import scala.xml.Elem

final class Haftarah(val customs: Custom.Of[Seq[ProphetSpan]]) {
  override def toString: String = toString(LanguageSpec.empty)

  def toString(spec: LanguageSpec): String = {
    customs.toSeq.map { case (custom, spans) =>
      custom.toString(spec) + ": " + ProphetSpan.toString(spans, spec)
    }.mkString("\n")
  }
}

object Haftarah {
  lazy val haftarah: Map[Parsha, Haftarah] = Metadata.loadMetadata(
    keys = Parsha.values,
    obj = this,
    resourceName = "Haftarah",
    rootElementName = "metadata",
    elementName = "week"
  ).mapValues { metadata =>
    val weekSpan = SpanParser.parseProphetSpan(metadata.attributes)
    metadata.attributes.close()

    val customElements = XML.span(metadata.elements, "custom")

    val result: Map[Set[Custom], Seq[ProphetSpan]] =
      if (customElements.isEmpty) Map(Set[Custom](Custom.Common) -> Seq(weekSpan.resolve))
      else customElements.map(element => parseCustom(element, weekSpan)).toMap

    new Haftarah(Custom.denormalize(result))
  }

  private def parseCustom(element: Elem, weekSpan: ProphetSpanParsed): (Set[Custom], Seq[ProphetSpan]) = {
    val (attributes, elements) = XML.open(element, "custom")
    val customs: Set[Custom] = Custom.parse(attributes.doGet("n"))

    val customSpan = SpanParser.parseProphetSpan(attributes)
    attributes.close()
    val contextSpan = customSpan.inheritFrom(weekSpan)

    val partElements = XML.span(elements, "part")

    val result: Seq[ProphetSpan] =
      if (partElements.isEmpty) Seq(contextSpan.resolve)
      else parseParts(partElements, contextSpan)

    customs -> result
  }

  private def parseParts(elements: Seq[Elem], contextSpan: ProphetSpanParsed): Seq[ProphetSpan] = {
    val result: Seq[NumberedProphetSpan] = elements.map(element =>
      SpanParser.parseNumberedProphetSpan(XML.openEmpty(element, "part"), contextSpan))
    WithNumber.checkConsecutive(result, "part")
    result.map(_.span)
  }

  private def printHaftarahList(custom: Custom, spec: LanguageSpec, full: Boolean): Unit = {
    println(custom.toString(spec))
    for (parsha <- Parsha.values) {
      val haftarah: Haftarah = Haftarah.haftarah(parsha)
      val customEffective: Custom = Custom.find(haftarah.customs, custom)
      val spans: Seq[ProphetSpan] = haftarah.customs(customEffective)
      val result: String = ProphetSpan.toString(spans, spec)

      if (customEffective == custom) {
        println(parsha.toString(spec) + ": " + result)
      } else if (full) {
        println(parsha.toString(spec) + " [" + customEffective.toString(spec)  + "]" + ": " + result)
      }
    }
  }

  def main(args: Array[String]): Unit = {
    def printSpans(spans: Seq[Span]): Unit = spans.zipWithIndex.foreach { case (span, index) =>
      println(s"${index+1}: $span")
    }

    printSpans(Parsha.Mattos.daysCombined(Custom.Ashkenaz))

    printHaftarahList(Custom.Shami, LanguageSpec(Language.Hebrew), full = false)
  }
}
