package io.scalac.mesmer.core.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigException

import scala.language.implicitConversions
import scala.reflect.ClassTag
import scala.reflect.classTag
import scala.util.Try

import io.scalac.mesmer.core.config.ConfigurationUtils.ConfigOps

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

trait ConfigurationBase {
  protected val configurationBase: String

  type Result

  protected def defaultConfig: Result
}

trait Configuration extends ConfigurationBase {

  protected implicit def toConfigOps(config: Config): ConfigOps = new ConfigOps(config)

  def fromConfig(config: Config): Result = config
    .tryValue(configurationBase)(_.getConfig)
    .map(extractFromConfig)
    .getOrElse(defaultConfig)

  protected def extractFromConfig(config: Config): Result
}

trait MesmerConfigurationBase extends ConfigurationBase {

  /**
   * Absolute path for mesmer configuration in .conf file
   */
  private final val mesmerBase: String = "io.scalac.mesmer"

  /*
    We explicitly make it public here
   */
  lazy val configurationBase: String =
    if (mesmerConfig.isEmpty) mesmerBase else s"$mesmerBase.$mesmerConfig"

  /**
   * Name of configuration inside mesmer branch
   */
  protected def mesmerConfig: String
}

trait MesmerConfiguration[T] extends MesmerConfigurationBase {
  type Result = T
}
