package org.opentorah.texts.tanach

import org.opentorah.metadata.{Metadata, Names, WithNumber}
import org.opentorah.texts.tanach.Torah.Numbered
import org.opentorah.util.Collections
import org.opentorah.xml.{Antiparser, Attribute, ContentType, Element, Parser}

final class ParshaMetadata(
  val parsha: Parsha,
  val names: Names,
  val span: Span,
  val days: Torah.Customs,
  val daysCombined: Option[Torah.Customs],
  val aliyot: Torah,
  val maftir: Torah.Maftir
)

object ParshaMetadata {

  final class Parsed(
    val parsha: Parsha,
    val names: Names,
    val span: SpanSemiResolved,
    val days: Custom.Sets[Seq[Torah.Numbered]],
    val daysCombined: Custom.Sets[Seq[Torah.Numbered]],
    val aliyot: Seq[Torah.Numbered],
    val maftir: SpanSemiResolved
) {
    def resolve(
      parshaSpan: Span,
      daysCombined: Option[Torah.Customs]
    ): Parser[ParshaMetadata] = {
      for {
        days <- daysResolved(parshaSpan)
        aliyot <- aliyotResolved(parshaSpan, days)
        maftir = maftirResolved(parshaSpan)
      } yield new ParshaMetadata(
        parsha,
        names,
        parshaSpan,
        days,
        daysCombined,
        aliyot,
        maftir
      )
    }

    private def daysResolved(parshaSpan: Span): Parser[Torah.Customs] =
      Torah.processDays(parsha.book, days, parshaSpan)

    private def aliyotResolved(parshaSpan: Span, days: Torah.Customs): Parser[Torah] = {
      val bookSpan = Torah.inBook(parsha.book,
        Span(
          parshaSpan.from,
          aliyot.last.what.to.getOrElse(days.common.spans.head.span.to)
        )
      )
      Torah.parseAliyot(bookSpan, aliyot, number = Some(3))
    }

    private def maftirResolved(parshaSpan: Span): Torah.Maftir = {
      val span = Span(maftir.from, maftir.to.getOrElse(parshaSpan.to))

      Torah.inBook(parsha.book,
        SpanSemiResolved.setImpliedTo(
          Seq(maftir),
          span,
          parsha.book.chapters
        ).head
      )
    }
  }

  private final case class DayParsed(
    span: Torah.Numbered,
    custom: Set[Custom],
    isCombined: Boolean
  )

  def parser(book: Tanach.ChumashBook): Parser[Parsed] = for {
    names <- Names.withoutDefaultNameParser
    span <- semiResolvedParser
    aliyot <- Aliyah.all
    daysParsed <- Day.all
    maftir <- Maftir.required
    parsha <- Metadata.find[Parsha](book.parshiot, names)
  } yield {
    val (days: Seq[DayParsed], daysCombined: Seq[DayParsed]) = daysParsed.partition(!_.isCombined)
    new Parsed(
      parsha,
      names,
      span,
      days = byCustom(days),
      daysCombined = byCustom(daysCombined),
      aliyot,
      maftir
    )
  }

  private def byCustom(days: Seq[DayParsed]): Custom.Sets[Seq[Torah.Numbered]] =
    Collections.mapValues(days.groupBy(_.custom))(days => days.map(_.span))

  private object Day extends Element[DayParsed]("day") {
    override def parser: Parser[DayParsed] = for {
      span <- numberedParser
      custom <- Attribute("custom").optional.map(_.fold[Set[Custom]](Set(Custom.Common))(Custom.parse))
      isCombined <- new Attribute.BooleanAttribute("combined").optional.map(_.getOrElse(false))
    } yield DayParsed(span, custom, isCombined)

    override def antiparser: Antiparser[DayParsed] = ???
  }

  object Aliyah extends Element[Torah.Numbered]("aliyah") {
    override def contentType: ContentType = ContentType.Empty
    override def parser: Parser[Numbered] = numberedParser
    override def antiparser: Antiparser[Numbered] = ???
  }

  object Maftir extends Element[SpanSemiResolved]("maftir") {
    override def parser: Parser[SpanSemiResolved] = semiResolvedParser
    override def antiparser: Antiparser[SpanSemiResolved] = ???
  }

  private def numberedParser: Parser[Torah.Numbered] = WithNumber.parse(semiResolvedParser)

  private def semiResolvedParser: Parser[SpanSemiResolved] = SpanParsed.parser.map(_.semiResolve)
}
