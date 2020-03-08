package org.opentorah.tei

import org.opentorah.xml.RawXml
import scala.xml.Node

final case class NotesStmt(xml: Seq[Node]) extends RawXml(xml)

object NotesStmt extends RawXml.Descriptor[NotesStmt]("notesStmt", new NotesStmt(_))