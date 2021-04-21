package io.scalac.mesmer.extension.config

import com.typesafe.config.Config

import scala.concurrent.duration._
import scala.jdk.DurationConverters._

import io.scalac.mesmer.extension.config

case class AkkaMonitoringConfig(
  autoStart: AutoStartSettings,
  cleaning: CleaningSettings
)

case class AutoStartSettings(akkaActor: Boolean, akkaHttp: Boolean, akkaPersistence: Boolean, akkaCluster: Boolean)

object AkkaMonitoringConfig {

  import config.ConfigurationUtils._

  private val autoStartDefaults =
    AutoStartSettings(akkaActor = false, akkaHttp = false, akkaCluster = false, akkaPersistence = false)
  private val cleaningSettingsDefaults = CleaningSettings(20.seconds, 5.second)

  private val akkaMonitoringDefaults = AkkaMonitoringConfig(autoStartDefaults, cleaningSettingsDefaults)

  def apply(config: Config): AkkaMonitoringConfig =
    config
      .tryValue("io.scalac.akka-monitoring")(_.getConfig)
      .map { monitoringConfig =>
        val autoStartSettings = monitoringConfig
          .tryValue("auto-start")(_.getConfig)
          .map { autoStartConfig =>
            val akkaActor = autoStartConfig.tryValue("akka-actor")(_.getBoolean).getOrElse(autoStartDefaults.akkaActor)
            val akkaHttp  = autoStartConfig.tryValue("akka-http")(_.getBoolean).getOrElse(autoStartDefaults.akkaHttp)
            val akkaPersistence =
              autoStartConfig.tryValue("akka-persistence")(_.getBoolean).getOrElse(autoStartDefaults.akkaPersistence)
            val akkaCluster =
              autoStartConfig.tryValue("akka-cluster")(_.getBoolean).getOrElse(autoStartDefaults.akkaCluster)
            AutoStartSettings(akkaActor, akkaHttp, akkaPersistence, akkaCluster)
          }
          .getOrElse(autoStartDefaults)

        val cleaningSettings = monitoringConfig
          .tryValue("cleaning")(_.getConfig)
          .flatMap { cleaningConfig =>
            for {
              max   <- cleaningConfig.tryValue("max-staleness")(_.getDuration)
              every <- cleaningConfig.tryValue("every")(_.getDuration)
            } yield CleaningSettings(max.toScala, every.toScala)
          }
          .getOrElse(cleaningSettingsDefaults)

        AkkaMonitoringConfig(
          autoStartSettings,
          cleaningSettings
        )
      }
      .getOrElse(akkaMonitoringDefaults)

}
