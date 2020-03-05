package org.digitaljudaica.xml

import zio.{Runtime, IO, ZIO}

object Parser {

  private[xml] def required[A](optional: Parser[Option[A]]): Parser[A] =
    required("parsable", optional)

  private[xml] def required[A](what: String, optional: Parser[Option[A]]): Parser[A] = for {
    result <- optional
    _ <- check(result.isDefined, s"Required $what is missing")
  } yield result.get


  def all[A](optional: Parser[Option[A]]): Parser[Seq[A]] =
    all(Seq.empty, optional)

  private def all[A](acc: Seq[A], optional: Parser[Option[A]]): Parser[Seq[A]] = for {
    next <- optional
    result <- next.fold[Parser[Seq[A]]](IO.succeed(acc))(next => all(acc :+ next, optional))
  } yield result

  def collectAll[A](parsers: Seq[Parser[A]]): Parser[Seq[A]] = for {
    runs <- ZIO.collectAll(parsers.map(_.either))
    errors: Seq[Error] = runs.flatMap(_.left.toOption)
    results: Seq[A] = runs.flatMap(_.right.toOption)
    results <- if (errors.nonEmpty) IO.fail(errors.mkString("Errors:\n  ", "\n  ", "\n.")) else IO.succeed(results)
  } yield results

  private[xml] def effect[A](f: => A): IO[Error, A] = IO(f).mapError(_.getMessage)

  def check(condition: Boolean, message: => String): IO[Error, Unit] =
    if (condition) IO.succeed(())
    else IO.fail(message)

  // TODO eliminate
  def parseDo[A](parser: Parser[A]): A =
    Parser.run(Parser.runnable(parser))

  // TODO eliminate (used only in tests)
  def parseOrError[A](parser: Parser[A]): Either[Error, A] =
    Parser.run(Parser.runnable(parser).either)

  def runnable[A](parser: Parser[A]): IO[Error, A] = {
    val result: Parser[A] = for {
      result <- parser
      isEmpty <- ZIO.access[Context](_.isEmpty)
      _ <- if (isEmpty) IO.succeed(()) else throw new IllegalStateException(s"Non-empty context $this!")
    } yield result

    result.provide(new Context)
  }

  final def run[A](toRun: IO[Error, A]): A =
    Runtime.default.unsafeRun(toRun.mapError(error => throw new IllegalArgumentException(error)))
}
