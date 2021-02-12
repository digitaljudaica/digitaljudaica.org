package org.opentorah.collector

import org.opentorah.xml.{Html, Xml}

trait HtmlContent {
  def viewer: Viewer = Viewer.default
  def isWide: Boolean = false
  def htmlHeadTitle: Option[String]
  def htmlBodyTitle: Option[Xml.Nodes] = None

  final def a(site: Site, part: Option[String] = None): Html.a = site.a(path(site), part)

  def path           (site: Site): Store.Path
  def navigationLinks(site: Site): Seq[Xml.Element] = Seq.empty
  def content        (site: Site): Xml.Element
}
