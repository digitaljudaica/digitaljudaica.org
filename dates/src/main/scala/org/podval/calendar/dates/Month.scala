/*
 * Copyright 2011 Podval Group.
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


final class Month(val number: Int) {

    require(0 < number)


    override def equals(other: Any): Boolean = other match {
        case that: Month => (number == that.number)
        case _ => false
    }


    override def hashCode = number


    override def toString: String = number.toString


    def cycle: Int = ((number - 1) / Year.MonthsInCycle) + 1


    def numberInCycle: Int = ((number - 1) % Year.MonthsInCycle) + 1


    def year: Year = Year(this)


    def numberInYear: Int = numberInCycle - year.monthsBeforeInCycle


    def day(day: Int): Day = {
        require (0 < day && day <= length)
        Day(firstDay + day - 1)
    }


    def firstDay: Int = year.firstDay + descriptor.daysBefore


    def newMoon: Moment = Month.FirstNewMoon + Month.MeanLunarPeriod*(number-1) 


    def name: MonthName.MonthName = descriptor.name


    // KH 8:5,6
    def length: Int = descriptor.length


    private def descriptor = year.months(numberInYear - 1)
}


object Month {

    // Mean lunar period: 29 days 12 hours 793 parts (KH 6:3 )
    val MeanLunarPeriod = Moment(29, 12, 793)


    // Molad of the year of Creation (#1; Man was created on Rosh Hashono of the year #2):
    // BeHaRaD: 5 hours 204 parts at night of the second day of Creation (KH 6:8)
    val FirstNewMoon = Day(2).momentOfNight(5, 204)


    def apply(number: Int): Month = new Month(number)


    def main(args: Array[String]) {
        // TODO move into a test
        println(Year(   1).month(1).newMoon.toMinutesString)
        println(Year(5772).month(2).newMoon.toMinutesString)
        println(Year(5772).month(3).newMoon.toMinutesString)
        println(Year(5772).month(4).newMoon.toMinutesString)
    }
}
