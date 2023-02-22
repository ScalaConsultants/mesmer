package io.scalac.mesmer.core.util

import scala.util.Try

object ReflectionUtils {

  def reflectiveIsInstanceOf(className: String, ref: Any): Either[String, Unit] =
    Try(Class.forName(className)).toEither.left.map {
      case _: ClassNotFoundException => s"Class $className not found"
      case e                         => e.getMessage
    }.filterOrElse(_.isInstance(ref), s"Ref $ref is not instance of $className").map(_ => ())

}
