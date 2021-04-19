package io.scalac.extension.config

import com.typesafe.config.Config

import io.scalac.core.model.Module
import io.scalac.extension.config.ConfigurationUtils._

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

case class BufferConfig private (size: Int)
