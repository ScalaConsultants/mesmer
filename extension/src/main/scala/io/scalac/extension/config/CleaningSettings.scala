package io.scalac.extension.config

import scala.concurrent.duration.FiniteDuration

case class CleaningSettings(maxStaleness: FiniteDuration, every: FiniteDuration)
