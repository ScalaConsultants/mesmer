package io.scalac.mesmer.core

sealed trait PathMatcher[T] extends (String => Boolean) with Ordered[PathMatcher[T]] {
  import PathMatcher._

  def value: T

  final def apply(path: String): Boolean = matches(path)

  def matches(path: String): Boolean = this match {
    case Exact(base, _) if path == base || path == s"$base/" => true
    case Prefix(base, _) if path.startsWith(base)            => true
    case _                                                   => false
  }

  def compare(that: PathMatcher[T]): Int = (this, that) match {
    case (Exact(_, _), Prefix(_, _)) => 1
    case (Prefix(_, _), Exact(_, _)) => -1
    case (Prefix(first, _), Prefix(second, _)) =>
      comparePath(first, second)
    case (Exact(first, _), Exact(second, _)) =>
      comparePath(first, second)
  }

  private def comparePath(first: String, second: String): Int =
    if (first.startsWith(second)) 1
    else if (second.startsWith(first)) -1
    else 0
}

object PathMatcher {

  private[core] final case class Prefix[T](base: String, value: T) extends PathMatcher[T]

  private[core] final case class Exact[T](path: String, value: T) extends PathMatcher[T]

  def parse[T](path: String, value: T): Option[PathMatcher[T]] = if (path.startsWith("/")) {
    path match {
      case _ if path.endsWith("/*") => Some(Prefix(path.substring(0, path.length - 2), value))
      case _ if !path.contains("*") =>
        Some(
          Exact(
            if (path.endsWith("/")) path.substring(0, path.length - 1) else path,
            value
          )
        )
      case _ => None
    }
  } else None

}
