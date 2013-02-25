package org.podval.calendar.dates;

import org.junit.{Before, Test, Assert}

import JewishCalendar.{Year, Day, Month}
import JewishCalendar.Month.{Tishrei, Marheshvan, AdarII, Nisan}


final class DatesTest {

  @Before
  def triggerInitializationOfTheJewishCalendar {
    val x = JewishCalendar
  }


  @Test
  def dayOfWeek {
    Assert.assertEquals(6, Day(   2, Tishrei   ,  1).dayOfWeek)
    Assert.assertEquals(2, Day(5772, Marheshvan, 24).dayOfWeek)
  }


  @Test
  def date2days2date() {
    val x = JewishCalendar
    date2days2date(1   , Tishrei,  1);
    date2days2date(2   , Tishrei,  1);
    date2days2date(5768, AdarII , 28);
    date2days2date(5769, Nisan  , 14);
  }


  private def date2days2date(yearNumber: Int, monthName: Month.Name, dayNumber: Int) {
    val year = Year(yearNumber)
    Assert.assertEquals(yearNumber, year.number)

    val month = year.month(monthName)
    Assert.assertEquals(monthName, month.name)

    val day = month.day(dayNumber)
    Assert.assertEquals(year, day.year)
    Assert.assertEquals(month, day.month)
    Assert.assertEquals(dayNumber, day.dayOfMonth)
  }
}
