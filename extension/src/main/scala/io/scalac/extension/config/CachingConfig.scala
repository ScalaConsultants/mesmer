package io.scalac.extension.config

import com.typesafe.config.Config
import io.scalac.core.model.Module
import io.scalac.extension.config.ConfigurationUtils.ConfigOps

case class CachingConfig private (maxEntries: Int)

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

case class BufferConfig private (size: Int)

object BufferConfig {
  private val DefaultSize = 1024

  def fromConfig(config: Config, module: Module): BufferConfig =
    (
      for {
        bufferConfig <- config.tryValue(s"io.scalac.akka-monitoring.internal-buffer.${module.name}")(_.getConfig)
        maxEntries = bufferConfig.tryValue("size")(_.getInt).getOrElse(DefaultSize)
      } yield BufferConfig(maxEntries)
    ).getOrElse(BufferConfig.empty)

  def empty: BufferConfig = BufferConfig(DefaultSize)
}
