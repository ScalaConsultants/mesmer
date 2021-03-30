package io.scalac.extension.config

import scala.reflect.ClassTag
import scala.reflect.classTag
import scala.util.Try

import com.typesafe.config.Config
import com.typesafe.config.ConfigException

object ConfigurationUtils {

  private[extension] implicit class ConfigOps(private val value: Config) extends AnyVal {
    def tryValue[T: ClassTag](
      path: String
    )(extractor: Config => String => T): Either[String, T] =
      Try(Function.uncurried(extractor)(value, path)).toEither.left.map {
        case _: ConfigException.Missing => s"Configuration for ${path}"
        case _: ConfigException.WrongType =>
          s"${path} is not type of ${classTag[T].runtimeClass}"
      }
  }
}
