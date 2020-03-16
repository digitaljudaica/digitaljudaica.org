package org.opentorah.xml

import java.net.URL
import zio.{IO, ZIO}
import scala.xml.{Elem, Node}

final private[xml] class Context {

  private var stack: List[Current] = List.empty

  private def current: Current =
    stack.head

  private def replaceCurrent[A](newCurrent: Current): Unit =
    stack = newCurrent :: stack.tail

  private def push(element: Current): Unit =
    stack = element :: stack

  private def pop(): Unit =
    stack = stack.tail

  private def isEmpty: Boolean =
    stack.isEmpty

  private def currentFromUrl: Option[URL] =
    stack.flatMap(_.from).head.url

  private def currentToString: String =
    stack.headOption.map(_.toString).getOrElse("")
}

private[xml] object Context {
  val isEmpty: Parser[Boolean] =
    ZIO.access[Context](_.isEmpty)

  val nextElementName: Parser[Option[String]] =
    lift(current => Content.getNextElementName(current.content))

  val nextElement: Parser[Option[Elem]] =
    liftContentModifier(Content.takeNextElement)

  val elementName: Parser[String] =
    lift(_.name)

  def takeAttribute(name: String): Parser[Option[String]] =
    liftCurrentModifier(Current.takeAttribute(name))

  val takeAllAttributes: Parser[Map[String, String]] =
    liftCurrentModifier(Current.takeAllAttributes)

  val takeCharacters: Parser[Option[String]] =
    liftContentModifier(Content.takeCharacters)

  val allNodes: Parser[Seq[Node]] =
    liftContentModifier(Content.takeAllNodes)

//  val allElements: Parser[Seq[Elem]] =
//    liftContentModifier(Content.takeAllElements)

  private def lift[A](f: Current => A): Parser[A] =
    ZIO.access[Context](liftCurrentToContext(f))

  private def liftCurrentModifier[A]: Current.Modifier[A] => Parser[A] = (f: Current.Modifier[A]) => for {
    result <- ZIO.accessM[Context](liftCurrentToContext(f))
    _ <- ZIO.access[Context](_.replaceCurrent(result._1))
  } yield result._2

  private def liftContentModifier[A]: Content.Modifier[A] => Parser[A] =
    liftCurrentModifier[A] compose liftContentModifierToCurrentModifier[A]

  private def liftCurrentToContext[A](f: Current => A): Context => A =
    (context: Context) => f(context.current)

  private def liftContentModifierToCurrentModifier[A](f: Content.Modifier[A]): Current.Modifier[A] =
    (current: Current) =>
      f(current.content).map { case (content, result) => (current.copy(content = content), result) }

  def currentFromUrl: Parser[Option[URL]] =
    ZIO.access[Context](_.currentFromUrl)

  def nested[A](
    from: Option[From],
    elem: Elem,
    contentType: ContentType,
    parser: Parser[A]
  ): Parser[A] = for {
    newCurrent <- Current.open(from, elem, contentType)
    result <- nested(newCurrent, parser)
  } yield result

  // TODO unfold
  private def nested[A](newCurrent: Current, parser: Parser[A]): Parser[A] =
    ZIO.access[Context](_.push(newCurrent)).bracket[Context, Error, A](
      release = (_: Unit) => ZIO.access[Context](_.pop()),
      use = (_: Unit) => addErrorTrace(for {
        result <- parser
        _ <- checkNoLeftovers
      } yield result)
    )

  // TODO unfold
  private def addErrorTrace[A](parser: Parser[A]): Parser[A] = parser.flatMapError(error => for {
    contextStr <- ZIO.access[Context](_.currentToString)
  } yield error + "\n" + contextStr)

  def checkNoLeftovers: Parser[Unit] = for {
    isEmpty <- ZIO.access[Context](_.isEmpty)
    _ <- if (isEmpty) IO.succeed(()) else ZIO.accessM(liftCurrentToContext(Current.checkNoLeftovers))
  } yield ()
}
