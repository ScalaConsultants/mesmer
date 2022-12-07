package io.scalac.mesmer.otelextension.instrumentations.akka.stream

import akka.actor.ActorSystem
import io.scalac.mesmer.core.config.ConfigurationUtils.toConfigOps

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.DurationConverters.JavaDurationOps

object AkkaStreamConfig {
  def metricSnapshotRefreshInterval(system: ActorSystem): FiniteDuration =
    system.settings.config
      .tryValue("io.scalac.mesmer.akka.streams.refresh-interval")(_.getDuration)
      .map(_.toScala)
      .getOrElse(10.seconds)

  def metricSnapshotCollectInterval(system: ActorSystem): FiniteDuration =
    system.settings.config
      .tryValue("io.scalac.mesmer.akka.streams.collect-interval")(_.getDuration)
      .map(_.toScala)
      .getOrElse(5.seconds)
}
