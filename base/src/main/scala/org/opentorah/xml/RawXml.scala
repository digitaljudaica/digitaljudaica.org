package org.opentorah.xml

class RawXml(elementName: String, namespace: Option[Namespace] = None) {

  final class Value(val xml: Xml.Nodes)

  object element extends Element[Value](elementName) {

    override def toString: String = s"raw element ${RawXml.this.elementName}"

    override def contentType: ContentType = ContentType.Mixed

    override def contentParsable: Parsable[Value] = new Parsable[Value] {
      override def parser: Parser[Value] = Element.nodes().map(new Value(_))

      override def unparser: Unparser[Value] = Unparser(
        content = _.xml,
        namespace = namespace
      )
    }
  }
}