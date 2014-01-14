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


sealed trait Editions {
  def isNo: Boolean = false
}

object NoEditions extends Editions {
  override def isNo: Boolean = true
}

final class LinearEditions(val editions: Seq[Edition]) extends Editions
final class DiffEdition(val edition1: Edition, val edition2: Edition) extends Editions
final class SingleEdition(val edition: Edition) extends Editions



object Editions {

  // TODO for more precise error reporting - exceptions and exception mapper?

  def apply(work: Work, editionNames: String): Editions = {
    if (editionNames.contains('+')) {
      val names: Seq[String] = editionNames.split('+')
      new LinearEditions(names.map(work.getEditionByName(_)))
      // TODO diff view
      //    } else if (editionNames.contains('-')) {
    } else {
      new SingleEdition(work.getEditionByName(editionNames))
    }
  }
}
