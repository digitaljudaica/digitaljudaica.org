package org.podval.calendar.dates.time

import org.podval.calendar.numbers.NotRangedHeadDigitNumberSystem

// TODO turn into a trait (without compilation errors)?
abstract class TimeNumberSystem[S <: TimeNumberSystem[S]]
  extends NotRangedHeadDigitNumberSystem[S]
{ this: S =>
  type Point <: TimePointBase[S]

  type Interval <: TimeIntervalBase[S]

  final val hoursPerDay = 24
  require(hoursPerDay % 2 == 0)

  final val partsPerHour = 1080

  final val momentsPerPart = 76

  final val hoursPerHalfDay: Int = hoursPerDay / 2

  final val minutesPerHour = 60

  require(partsPerHour % minutesPerHour == 0)

  final val partsPerMinute: Int = partsPerHour / minutesPerHour

  final override def range(position: Int): Int = position match {
    case 0 => hoursPerDay
    case 1 => partsPerHour
    case 2 => momentsPerPart
  }

  final override def headSign: String = "d"

  final override val signPartial: PartialFunction[Int, String] = {
    case 0 => "h"
    case 1 => "p"
    case 2 => "m"
  }
}
