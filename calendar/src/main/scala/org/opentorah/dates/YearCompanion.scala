package org.opentorah.dates

import org.opentorah.numbers.NumbersMember
import org.opentorah.util.Cache

/**
  *
  */
trait YearCompanion[C <: Calendar[C]] extends NumbersMember[C] {
  private final val yearsCache: Cache[Int, C#Year] = new Cache[Int, C#Year] {
    override def calculate(number: Int): C#Year = newYear(number)
  }

  final def apply(number: Int): C#Year =
    yearsCache.get(number, numbers.cacheYears)

  protected def newYear(number: Int): C#Year

  // lazy to make initialization work
  lazy val monthDescriptors: Map[C#YearCharacter, Seq[C#MonthDescriptor]] =
    Map((for (character <- characters) yield character -> monthsGenerator(character)): _*)

  protected def characters: Seq[C#YearCharacter]

  private[this] def monthsGenerator(character: C#YearCharacter): Seq[numbers.MonthDescriptor] = {
    val namesAndLengths = monthNamesAndLengths(character)
    val daysBeforeForMonth: Seq[Int] = namesAndLengths.map(_.length).scanLeft(0)(_ + _).init
    namesAndLengths zip daysBeforeForMonth map { case (nameAndLength, daysBefore) =>
      // TODO get rid of the cast!
      new numbers.MonthDescriptor(nameAndLength.name.asInstanceOf[numbers.Month.Name], nameAndLength.length, daysBefore)
    }
  }

  protected def monthNamesAndLengths(character: C#YearCharacter): Seq[C#MonthNameAndLength]

  protected final def yearLength(character: C#YearCharacter): Int = {
    val lastMonth: C#MonthDescriptor = monthDescriptors(character).last
    lastMonth.daysBefore + lastMonth.length
  }

  protected def areYearsPositive: Boolean

  final def yearsForSureBefore(dayNumber: Int): Int =  {
    val result: Int = (4 * dayNumber / (4 * 365 + 1)) - 1
    if (areYearsPositive) scala.math.max(1, result) else result
  }

  def isLeap(yearNumber: Int): Boolean

  def firstMonth(yearNumber: Int): Int

  def lengthInMonths(yearNumber: Int): Int
}
