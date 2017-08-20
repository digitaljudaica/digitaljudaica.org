package org.podval.calendar.jewish

import org.podval.calendar.calendar.MomentBase
import org.podval.calendar.numbers.NumberSystem.RawNumber
import Jewish.Moment

abstract class JewishMoment(raw: RawNumber) extends MomentBase[Jewish](raw) {
  final def nightHours(value: Int): Moment = firstHalfHours(value)

  final def dayHours(value: Int): Moment = secondHalfHours(value)
}
