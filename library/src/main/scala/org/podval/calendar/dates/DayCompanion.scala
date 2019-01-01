package org.podval.calendar.dates

import org.podval.judaica.metadata.NamedCompanion

/**
  *
  */
abstract class DayCompanion[C <: Calendar[C]] extends CalendarMember[C] {
  val Name: NamedCompanion

  final type Name = Name.Key

  def names: Seq[C#DayName]

  def apply(number: Int): C#Day

  final def apply(year: Int, month: C#MonthName, day: Int): C#Day =
    calendar.Year(year).month(month).day(day)

  final def apply(year: Int, month: Int, day: Int): C#Day =
    calendar.Year(year).month(month).day(day)

  final def numberInWeek(dayNumber: Int): Int =
    ((dayNumber + firstDayNumberInWeek - 1 - 1) % Calendar.daysPerWeek) + 1

  val firstDayNumberInWeek: Int

  final def now: C#Day = calendar.Moment.now.day
}
