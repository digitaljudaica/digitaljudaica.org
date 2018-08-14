package org.podval.calendar.astronomy

import org.scalatest.{FlatSpec, Matchers}
import org.podval.calendar.jewish.Jewish.Year

class SeasonsTest extends FlatSpec with Matchers {
  "tkufas Nisan" should "calculate correctly" in {
    val seasons = new SeasonsAstronomical(Calculator.Text)
//    println(seasons.tkufasNisan(Year(5778)))
  }
}
