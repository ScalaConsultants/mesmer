package io.scalac.extension.config

import com.typesafe.config.Config
import ConfigurationUtils.ConfigOps

import CachingConfig._

case class CachingConfig(
  maxEntries: Int = DefaultSize
)

object CachingConfig {
  val DefaultSize = 10

  def fromConfig(config: Config, module: String): CachingConfig =
    (
      for {
        cachingConfig <- config.tryValue(s"io.scalac.akka-monitoring.caching.$module")(_.getConfig)
        maxEntries = cachingConfig.tryValue("max-entries")(_.getInt).getOrElse(DefaultSize)
      } yield CachingConfig(maxEntries)
    ).getOrElse(CachingConfig.empty)

  def empty: CachingConfig = CachingConfig()
}
