package io.scalac.extension.config

import scala.concurrent.duration.FiniteDuration

case class FlushConfig(maxStaleness: Long, every: FiniteDuration)
