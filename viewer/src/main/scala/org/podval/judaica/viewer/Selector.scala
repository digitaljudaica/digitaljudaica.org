/*
 * Copyright 2012-2014 Leonid Dubinsky <dub@podval.org>.
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

import org.podval.judaica.xml.Xml.XmlOps

import scala.xml.Elem


final class Selectors private(override val named: Seq[Selector]) extends ByName[Selector]


abstract class Selector(override val names: Names, val selectors: Selectors) extends Named {

  def isNumbered: Boolean


  def asNumbered: NumberedSelector


  def asNamed: NamedSelector
}



final class NumberedSelector(names: Names, selectors: Selectors) extends Selector(names, selectors) {

  override def isNumbered: Boolean = true


  override def asNumbered: NumberedSelector = this


  override def asNamed: NamedSelector = throw new ClassCastException
}



final class NamedSelector(names: Names, selectors: Selectors) extends Selector(names, selectors) {

  override def isNumbered: Boolean = false


  override def asNumbered: NumberedSelector =  throw new ClassCastException


  override def asNamed: NamedSelector = this
}



// TODO better "compilation" error messages...
object Selectors {

  def apply(xml: Elem): Selectors = {
    val xmls = xml.elems("selectors", "selector")
    val names2xml: Map[Names, Elem] = xmls.map(xml => Names(xml) -> xml).toMap
    val names = names2xml.keySet

    // TODO calculate roots!

    val names2selectors: Map[Names, Seq[Names]] = names2xml.mapValues { selectorXml: Elem =>
      selectorXml.elems("selectors", "selector", required = false).map { subselectorXml: Elem =>
        val name = subselectorXml.getAttribute("name")
        names.find(_.has(name)).get
      }
    }

    val ordered: Seq[Names] = {
      val reachable = Orderer.close(names2selectors)
      require(Orderer.inCycles(reachable).isEmpty)
      Orderer.order(reachable) // TODO filter out only the roots
    }


    def build(acc: Seq[Selector], names2selector: Map[Names, Selector], left: Seq[Names]): Seq[Selector] =
      if (left.isEmpty) acc else {
        val names = left.head
        val selectors = new Selectors(names2selectors(names).map(names2selector))
        val xml = names2xml(names)
        val isNumbered: Boolean = xml.booleanAttribute("isNumbered")

        val selector: Selector =
          if (isNumbered)
            new NumberedSelector(names, selectors)
          else
            new NamedSelector(names, selectors)

        build(acc :+ selector, names2selector.updated(selector.names, selector), left.tail)
      }


    val selectors: Seq[Selector] = build(Seq.empty, Map.empty, ordered)

    // TODO can I somehow figure out - or mark? - top-level selectors? Only they should go into the result...

    new Selectors(selectors)
  }
}
