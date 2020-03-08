package org.opentorah.tei

import org.opentorah.xml.RawXml
import scala.xml.Node

final case class EncodingDesc(xml: Seq[Node]) extends RawXml(xml)

object EncodingDesc extends RawXml.Descriptor[EncodingDesc]("encodingDesc", new EncodingDesc(_))