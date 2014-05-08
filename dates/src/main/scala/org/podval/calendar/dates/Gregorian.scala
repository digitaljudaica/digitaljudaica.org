/*
 * Copyright 2011-2014 Podval Group.
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

package org.podval.calendar.dates


object Gregorian extends Calendar {

  // TODO assignments of the companion objects have to happen early on, but even this is not sufficient!
  // I found that I need to assign JewishCalendar to a val to trigger its initialization - or I end up with a null for the Year companion object!
  override protected val timeCompanion = Time
  override protected val momentCompanion = Moment
  override protected val dayCompanion = Day
  override protected val monthCompanion = Month
  override protected val yearCompanion = Year


  final class Year(number: Int) extends YearBase(number) {

    override def firstDay: Int = yearCompanion.firstDay(number)


    override def lengthInDays = yearCompanion.lengthInDays(number)


    override def character: yearCompanion.Character = isLeap
  }



  object Year extends YearCompanion {

    type Character = Boolean


    override def apply(number: Int): Year = new Year(number)


    protected override def characters: Seq[yearCompanion.Character] = Seq(true, false)


    protected override def namesAndLengths(isLeap: Boolean): List[(Month.Name, Int)] = {
      import Month._
      List(
        (January, 31),
        (February, if (isLeap) 29 else 28),
        (March, 31),
        (April, 30),
        (May, 31),
        (June, 30),
        (July, 31),
        (August, 31),
        (September, 30),
        (October, 31),
        (November, 30),
        (December, 31)
      )
    }


    protected override def areYearsPositive: Boolean = false


    // TODO give names to constants?

    override def isLeap(yearNumber: Int): Boolean = (yearNumber % 4 == 0) && ((yearNumber % 100 != 0) || (yearNumber % 400 == 0))


    override def firstMonth(yearNumber: Int): Int = monthsInYear*(yearNumber - 1) + 1


    override def lengthInMonths(yearNumber: Int): Int = monthsInYear


    val monthsInYear = 12


    private val daysInNonLeapYear = 365


    def firstDay(yearNumber: Int): Int = daysInNonLeapYear * (yearNumber - 1) + (yearNumber - 1)/4 - (yearNumber - 1)/100 + (yearNumber - 1)/400 + 1


    def lengthInDays(yearNumber: Int): Int = if (Gregorian.Year.isLeap(yearNumber)) daysInNonLeapYear + 1 else daysInNonLeapYear
  }


  final class Month(number: Int) extends MonthBase(number)


  object Month extends MonthCompanion {

    override def apply(number: Int): Month = new Month(number)


    sealed class Name(name: String) extends Named(name)

    case object January   extends Name("January")
    case object February  extends Name("February")
    case object March     extends Name("March")
    case object April     extends Name("April")
    case object May       extends Name("May")
    case object June      extends Name("June")
    case object July      extends Name("July")
    case object August    extends Name("August")
    case object September extends Name("September")
    case object October   extends Name("October")
    case object November  extends Name("November")
    case object December  extends Name("December")


    override def yearNumber(monthNumber: Int): Int = (monthNumber - 1) / Gregorian.Year.monthsInYear + 1


    override def numberInYear(monthNumber: Int): Int =  monthNumber - Gregorian.Year.firstMonth(yearNumber(monthNumber)) + 1
  }


  final class Day(number: Int) extends DayBase(number) {

    def morningTime(hours: Int, parts: Int): Moment = time(Time.morningTime(hours, parts))


    def afternoonTime(hours: Int, parts: Int): Moment = time(Time.afternoonTime(hours, parts))
  }


  object Day extends DayCompanion {

    sealed class Name(name: String) extends Named(name)

    case object Sunday    extends Name("Sunday")
    case object Monday    extends Name("Monday")
    case object Tuesday   extends Name("Tuesday")
    case object Wednesday extends Name("Wednesday")
    case object Thursday  extends Name("Thursday")
    case object Friday    extends Name("Friday")
    case object Saturday  extends Name("Saturday")


    def names: Seq[dayCompanion.Name] = Seq(Sunday, Monday, Tuesday, Wednesday, Thursday, Friday, Saturday)


    override def apply(number: Int): Day = new Day(number)


    val epoch = 1373429


    override val firstDayNumberInWeek =
      (((Jewish.Day.firstDayNumberInWeek - 1) + (epoch % Calendar.daysPerWeek)) % Calendar.daysPerWeek) + 1
  }


  object Moment extends MomentCompanion {

    override def apply(days: Int, time: Time): Moment = new Moment(days, time)
  }


  object Time extends TimeCompanion {

    override def apply(hours: Int, parts: Int) = new Time(hours, parts)


    def morningTime(hours: Int, parts: Int) = {
      require(hours < Calendar.hoursPerHalfDay)
      Time(hours, parts)
    }


    def afternoonTime(hours: Int, parts: Int) = {
      require(hours < Calendar.hoursPerHalfDay)
      Time(hours + Calendar.hoursPerHalfDay, parts)
    }
  }
}
