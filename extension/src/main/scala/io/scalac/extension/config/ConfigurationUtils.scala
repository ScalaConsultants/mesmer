package io.scalac.extension.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigException

import scala.language.implicitConversions
import scala.reflect.ClassTag
import scala.reflect.classTag
import scala.util.Try

import io.scalac.extension.config.ConfigurationUtils.ConfigOps

object ConfigurationUtils {

  private[extension] class ConfigOps(private val value: Config) extends AnyVal {
    def tryValue[T: ClassTag](
      path: String
    )(extractor: Config => String => T): Either[String, T] =
      Try(Function.uncurried(extractor)(value, path)).toEither.left.map {
        case _: ConfigException.Missing => s"Configuration for $path"
        case _: ConfigException.WrongType =>
          s"$path is not type of ${classTag[T].runtimeClass}"
      }
  }

  implicit def toConfigOps(config: Config): ConfigOps = new ConfigOps(config)
}

trait Configuration[T] {
  protected implicit def toConfigOps(config: Config): ConfigOps = new ConfigOps(config)

  def default: T
  def fromConfig(config: Config): T = config
    .tryValue(configurationBase)(_.getConfig)
    .map(extractFromConfig)
    .getOrElse(default)

  protected val configurationBase: String
  protected def extractFromConfig(config: Config): T
}
