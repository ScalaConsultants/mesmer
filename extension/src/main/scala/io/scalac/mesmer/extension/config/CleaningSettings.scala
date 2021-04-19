package io.scalac.mesmer.extension.config

import scala.concurrent.duration.FiniteDuration

case class CleaningSettings(maxStaleness: FiniteDuration, every: FiniteDuration)
