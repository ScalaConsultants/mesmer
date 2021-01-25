package io.scalac.extension.config

import com.typesafe.config.Config
import ConfigurationUtils.ConfigOps

import CachingConfig._

case class CachingConfig(
  maxEntries: Int = DefaultSize,
  loadFactor: Float = DefaultLoadFactor
)

object CachingConfig {
  val DefaultSize       = 10
  val DefaultLoadFactor = 0.1f

  def fromConfig(config: Config, module: String): CachingConfig =
    (
      for {
        cachingConfig <- config.tryValue(s"io.scalac.akka-monitoring.caching.$module")(_.getConfig)
        maxEntries    = cachingConfig.tryValue("max-entries")(_.getInt).getOrElse(DefaultSize)
        loadFactor    = cachingConfig.tryValue("load-factor")(_.getDouble).map(_.floatValue).getOrElse(DefaultLoadFactor)
      } yield CachingConfig(maxEntries, loadFactor)
    ).getOrElse(CachingConfig.empty)

  def empty: CachingConfig = CachingConfig()
}
