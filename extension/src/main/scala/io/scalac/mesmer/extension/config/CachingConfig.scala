package io.scalac.mesmer.extension.config

import com.typesafe.config.Config

import io.scalac.mesmer.core.model.Module
import io.scalac.mesmer.extension.config.ConfigurationUtils._

case class CachingConfig(maxEntries: Int)

object CachingConfig {
  private val DefaultSize = 10

  def fromConfig(config: Config, module: Module): CachingConfig =
    (
      for {
        cachingConfig <- config.tryValue(s"io.scalac.akka-monitoring.caching.${module.name}")(_.getConfig)
        maxEntries = cachingConfig.tryValue("max-entries")(_.getInt).getOrElse(DefaultSize)
      } yield CachingConfig(maxEntries)
    ).getOrElse(CachingConfig.empty)

  def empty: CachingConfig = CachingConfig(DefaultSize)
}
