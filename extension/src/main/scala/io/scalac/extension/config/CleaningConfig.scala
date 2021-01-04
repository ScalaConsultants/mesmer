package io.scalac.extension.config

import scala.concurrent.duration.FiniteDuration

case class CleaningConfig(maxStaleness: Long, every: FiniteDuration)
