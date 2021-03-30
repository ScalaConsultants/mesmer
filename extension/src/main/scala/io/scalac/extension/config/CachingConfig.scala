package io.scalac.extension.config

import com.typesafe.config.Config

import io.scalac.core.model.Module
import io.scalac.extension.config.CachingConfig._
import io.scalac.extension.config.ConfigurationUtils.ConfigOps

case class CachingConfig(
  maxEntries: Int = DefaultSize
)

object CachingConfig {
  val DefaultSize = 10

  def fromConfig(config: Config, module: Module): CachingConfig =
    (
      for {
        cachingConfig <- config.tryValue(s"io.scalac.akka-monitoring.caching.${module.name}")(_.getConfig)
        maxEntries = cachingConfig.tryValue("max-entries")(_.getInt).getOrElse(DefaultSize)
      } yield CachingConfig(maxEntries)
    ).getOrElse(CachingConfig.empty)

  def empty: CachingConfig = CachingConfig()
}
