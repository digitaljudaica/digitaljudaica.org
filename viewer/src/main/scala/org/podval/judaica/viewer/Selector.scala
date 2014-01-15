/*
 *  Copyright 2014 Leonid Dubinsky <dub@podval.org>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.podval.judaica.viewer

import org.podval.judaica.xml.Xml.Ops
import scala.xml.Elem


abstract class Selector(siblings: Seq[Selector], xml: Elem) extends Named with Selectors {
  def isNumbered: Boolean
  def asNumbered: NumberedSelector
  def asNamed: NamedSelector

  override val names = Names(xml)
  override val selectors = Selector.parseSelectors(siblings, xml)
  override def selectorByName(name: String): Option[Selector] = Names.find(selectors, name)
}


final class NumberedSelector(siblings: Seq[Selector], xml: Elem) extends Selector(siblings, xml) {
  override def isNumbered: Boolean = true
  override def asNumbered: NumberedSelector = this
  override def asNamed: NamedSelector = throw new ClassCastException
}


final class NamedSelector(siblings: Seq[Selector], xml: Elem) extends Selector(siblings, xml) {
  override def isNumbered: Boolean = false
  override def asNumbered: NumberedSelector = throw new ClassCastException
  override def asNamed: NamedSelector = this
}


trait Selectors {

  def selectors: Seq[Selector]


  def selectorByName(name: String): Option[Selector]


  def deepStructures: Seq[Seq[Selector]] =
    if (selectors.isEmpty) Seq(Nil) else
    selectors.flatMap(selector => selector.deepStructures.map (ds => selector +: ds))


  def descendants(next: Set[Selector]): Set[Selector] = descendants(Set.empty, next)


  def descendants(result: Set[Selector], next: Set[Selector]): Set[Selector] = {
    val add = next -- result
    if (add.isEmpty) result else {
      val children: Set[Selector] = add.flatMap(_.selectors)
      descendants(result ++ next, children)
    }
  }
}



object Selector {

  // TODO use "descendants" it in parseSelectors, to accommodate "day/chapter"

  def parseSelectors(uncles: Seq[Selector], xmls: Elem): Seq[Selector] =
    xmls.elemsFilter("selector").foldLeft(Seq.empty[Selector])((siblings, xml) => siblings :+ parseSelector(uncles, siblings, xml))


  private def parseSelector(uncles: Seq[Selector], siblings: Seq[Selector], xml: Elem): Selector = {
    val nameOption = xml.attributeOption("name")
    if (nameOption.isDefined) {
      // A reference to a previously defined Selector
      Exists(uncles, nameOption.get, "selector")
    } else {
      if (xml.booleanAttribute("isNumbered"))
        new NumberedSelector(siblings, xml)
      else
        new NamedSelector(siblings, xml)
    }
  }
}
