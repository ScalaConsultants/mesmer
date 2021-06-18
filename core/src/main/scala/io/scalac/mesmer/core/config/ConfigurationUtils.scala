package io.scalac.mesmer.core.config

import com.typesafe.config.{ Config, ConfigException }
import io.scalac.mesmer.core.config.ConfigurationUtils.ConfigOps

import scala.language.implicitConversions
import scala.reflect.{ classTag, ClassTag }
import scala.util.Try

object ConfigurationUtils {

  private[core] class ConfigOps(private val value: Config) extends AnyVal {
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

trait Configuration {
  protected implicit def toConfigOps(config: Config): ConfigOps = new ConfigOps(config)

  type Result

  protected def defaultConfig: Result
  def fromConfig(config: Config): Result = config
    .tryValue(absoluteBase)(_.getConfig)
    .map(extractFromConfig)
    .getOrElse(defaultConfig)

  protected val absoluteBase: String
  protected def extractFromConfig(config: Config): Result
}

trait MesmerConfigurationBase extends Configuration {

  /**
   * Absolute path for mesmer configuration in .conf file
   */
  private final val mesmerBase: String = "io.scalac.mesmer"

  protected lazy val absoluteBase = s"$mesmerBase.$mesmerConfig"

  /**
   * Name of configuration inside mesmer branch
   */
  protected def mesmerConfig: String
}

trait MesmerConfiguration[T] extends MesmerConfigurationBase {
  type Result = T
}
