/*
 *  Copyright 2013 Leonid Dubinsky <dub@podval.org>.
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
 * under the License.
 */

package org.podval.judaica.xml

import scala.xml.Elem

object App {

  def apply(read: Elem, write: String): Elem =
    <app>
      <rdg type="write">
        {Word(write)}
      </rdg>
      <rdg type="read">
        {read}
      </rdg>
    </app>


  def unapply(elem: Elem): Option[(Elem, String)] = {
    if (elem.label != "app") None else {
      val readings: Seq[Elem] = (elem \ "rdg").map(_.asInstanceOf[Elem])
      val read = readings.find(Xml.getAttribute(_, "type") ==  "read")
      val write = readings.find(Xml.getAttribute(_, "type") == "write")
      Some((
        Xml.oneChild(read.get, "div"),
        Xml.oneChild(write.get, "div").text
      ))
    }
  }
}