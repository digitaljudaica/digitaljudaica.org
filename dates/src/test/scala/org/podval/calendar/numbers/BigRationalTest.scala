package org.podval.calendar.numbers

import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalacheck.{Arbitrary, Gen}
import Arbitrary.arbitrary

import BigRational.{one, oneHalf, zero}

final class BigRationalTest extends FlatSpec with GeneratorDrivenPropertyChecks with Matchers {
  val minusThreeHalfs: BigRational = BigRational(-3, 2)

  def rationals: Gen[BigRational] =
    for {
      numerator <- arbitrary[Int]
      denominator <- arbitrary[Int] if denominator != 0
    } yield BigRational(numerator, denominator)

  "apply()" should "be correct" in {
    assertThrows[ArithmeticException](BigRational(1, 0))
    assertResult(-one)(BigRational(-1, 1))
    assertResult(-one)(BigRational(1, -1))
  }

  "toString()" should "be correct" in {
    assertResult("0/1")(zero.toString)
    assertResult("1/2")(oneHalf.toString)
    assertResult("-3/2")(minusThreeHalfs.toString)
  }

  "signum()" should "be correct" in {
    assertResult(0)(zero.signum)
    assertResult(1)(oneHalf.signum)
    assertResult(-1)(minusThreeHalfs.signum)
  }

  "abs()" should "be correct" in {
    assertResult(zero)(zero.abs)
    assertResult(oneHalf)(oneHalf.abs)
    assertResult(-minusThreeHalfs)(minusThreeHalfs.abs)
  }

  "abs()" should "be idepmotent" in {
    forAll(rationals) { r => r.abs.abs should be (r.abs) }
  }

  "abs()" should "be determined by signum()" in {
    forAll(rationals) { r => if (r.signum < 0) r.abs should be (-r) else r.abs should be (r) }
  }

  "unary_-()" should "be correct" in {
    assertResult(zero)(-zero)
    assertResult(BigRational(-1, 2))(-oneHalf)
    assertResult(BigRational(3, 2))(minusThreeHalfs.abs)
  }

  "unary_-()" should "be idempotent" in {
    forAll(rationals) { r => -(-r) should be (r) }
  }

  "invert()" should "be correct" in {
    assertThrows[ArithmeticException](zero.invert)
    assertResult(one+one)(oneHalf.invert)
    assertResult(BigRational(-2, 3))(minusThreeHalfs.invert)
  }

  "invert()" should "be idempotent" in {
    forAll(rationals) { r => whenever (r.numerator != 0) { r.invert.invert should be (r) }}
  }

  "+()" should "be correct" in {
    assertResult(zero)(zero+zero)
    assertResult(-one)(oneHalf+minusThreeHalfs)
  }

  "+()" should "be commutative" in {
    forAll(rationals, rationals) { (l, r) => l + r should be (r + l) }
  }

  "+()" should "have 0 as unit" in {
    forAll(rationals) { r => r + zero should be (r) }
    forAll(rationals) { r => zero + r should be (r) }
  }

  "-()" should "be correct" in {
    assertResult(zero)(zero-zero)
    assertResult(one*2)(oneHalf-minusThreeHalfs)
  }

  "-()" should "be inverse of +()" in {
    forAll(rationals, rationals) { (l, r) => l + r - r should be (l) }
  }

  "-(), unary_-() and +()" should "be related correctly" in {
    forAll(rationals, rationals) { (l, r) => l - r should be (l + (-r)) }
  }

  "*(Int)" should "be correct" in {
    assertResult(zero)(zero*3)
    assertResult(-one)(oneHalf*(-2))
    assertResult(zero-one-one-one)(minusThreeHalfs*2)
  }

  "*(BigRational)" should "be correct" in {
    assertResult(zero)(zero*zero)
    assertResult(one/4)(oneHalf*oneHalf)
    assertResult(BigRational(-3, 4))(minusThreeHalfs*oneHalf)
  }

  "*(BigRational)" should "be commutative" in {
    forAll(rationals, rationals) { (l, r) => l * r should be (r * l) }
  }

  "*(BigRational)" should "have 1 as unit" in {
    forAll(rationals) { r => r * one should be (r) }
    forAll(rationals) { r => one * r should be (r) }
  }

  "/(Int)" should "be correct" in {
    assertResult(zero)(zero/3)
    assertResult(BigRational(-1, 4))(oneHalf/(-2))
    assertResult(BigRational(-3, 4))(minusThreeHalfs/2)
  }

  "/(BigRational)" should "be correct" in {
    assertThrows[ArithmeticException](zero/zero)
    assertResult(one)(oneHalf/oneHalf)
    assertResult(-(one+one+one))(minusThreeHalfs/oneHalf)
  }

  "/(BigRationa)" should "be inverse of *(BigRational)" in {
    forAll(rationals, rationals) { (l, r) => whenever (r.numerator != 0) { l * r / r should be (l) }}
  }

  "/(BigRational), inverse() and *(BigRationa)" should "be related correctly" in {
    forAll(rationals, rationals) { (l, r) => whenever (r.numerator != 0) { l / r should be (l * r.invert) }}
  }

  "wholeAndFraction()" should "be correct" in {
    val days: Int = 1
    val hours: Int = 2
    val parts: Int = 3
    val time: BigRational = BigRational(((days*24)+hours)*1080+parts, 1*24*1080)

    val (daysO: Int, remainderhp: BigRational) = time.wholeAndFraction
    assertResult(days)(daysO)

    val (hoursO: Int, remainderp: BigRational) = (remainderhp*24).wholeAndFraction
    assertResult(hours)(hours)

    val (partsO: Int, remainder: BigRational) = (remainderp*1080).wholeAndFraction
    assertResult(parts)(partsO)
    assert(remainder.numerator == 0)

    assertResult((0, zero))(zero.wholeAndFraction)
    assertResult((0, oneHalf))(oneHalf.wholeAndFraction)

    assertResult((-1, -oneHalf))(minusThreeHalfs.wholeAndFraction)
  }

  "==()" should "be correct" in {
    assert(zero == zero)
    assert(zero == BigRational(0))
    assert(zero != BigRational(1))
  }

  "compare()" should "be correct" in {
    assert(zero < oneHalf)
    assert(oneHalf <= one)
    assert(one > minusThreeHalfs)
  }
}
