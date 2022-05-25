package io.scalac.mesmer.core

sealed trait PathMatcher[T] extends (String => Boolean) with Ordered[PathMatcher[T]] {

  def value: T

  final def apply(path: String): Boolean = matches(path)

  def matches(path: String): Boolean

  protected def comparePath(first: String, second: String): Int =
    if (first.startsWith(second)) 1
    else if (second.startsWith(first)) -1
    else 0
}

object PathMatcher {

  private[core] final case class Prefix[T](base: String, recursive: Boolean, value: T) extends PathMatcher[T] {
    def matches(path: String): Boolean =
      path.startsWith(base) && (recursive || !path.substring(base.length).contains('/'))

    def compare(that: PathMatcher[T]): Int = that match {
      case _: Exact[_] => -1
      case another: Prefix[_] if another.recursive == recursive =>
        if (recursive) {
          if (base.startsWith(another.base)) {
            base.length - another.base.length // 0 when equal
          } else if (another.base.startsWith(base)) {
            -1
          } else 0
        } else 0

      case Prefix(recursiveBase, true, _) =>
        if (base.startsWith(recursiveBase) || recursiveBase.startsWith(base)) {
          1
        } else 0

      case Prefix(nonRecursiveBase, false, _) =>
        if (base.startsWith(nonRecursiveBase) || nonRecursiveBase.startsWith(base)) {
          -1
        } else 0
    }
  }

  private[core] final case class Exact[T](base: String, value: T) extends PathMatcher[T] {
    def matches(path: String): Boolean = path == base || s"$path/" == base

    def compare(that: PathMatcher[T]): Int = that match {
      case _: Prefix[_]          => 1
      case Exact(anotherBase, _) => comparePath(base, anotherBase)
    }
  }

  sealed trait Error {
    def message: String
  }

  object Error {
    case object InvalidWildcardError extends Error {
      val message = "Wildcard is permitted only at end of the path"
    }

    case object MissingSlashError extends Error {
      val message = "Path must start with a slash"
    }
  }

  def parse[T](path: String, value: T): Either[Error, PathMatcher[T]] = if (path.startsWith("/")) {
    path match {
      case _ if path.endsWith("/*")  => Right(Prefix(path.substring(0, path.length - 1), recursive = false, value))
      case _ if path.endsWith("/**") => Right(Prefix(path.substring(0, path.length - 2), recursive = true, value))
      case _ if !path.contains("*") =>
        Right(
          Exact(
            if (path.endsWith("/")) path else s"$path/",
            value
          )
        )
      case _ => Left(Error.InvalidWildcardError)
    }
  } else Left(Error.MissingSlashError)

}
