package io.scalac.extension.config

import com.typesafe.config.{Config, ConfigException}

import scala.reflect.{ClassTag, classTag}
import scala.util.Try

object ConfigurationUtils {

  private[extension] implicit class ConfigOps(val value: Config) extends AnyVal {
    def tryValue[T: ClassTag](
      path: String
    )(extractor: Config => String => T): Either[String, T] = {
      Try(Function.uncurried(extractor)(value, path)).toEither.left.map {
        case _: ConfigException.Missing => s"Configuration for ${path}"
        case _: ConfigException.WrongType =>
          s"${path} is not type of ${classTag[T].runtimeClass}"
      }
    }
  }
}
