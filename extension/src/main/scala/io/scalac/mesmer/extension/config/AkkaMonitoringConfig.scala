package io.scalac.mesmer.extension.config

import com.typesafe.config.Config

import scala.concurrent.duration._
import scala.jdk.DurationConverters._

import io.scalac.mesmer.core.config.Configuration
import io.scalac.mesmer.core.config.MesmerConfiguration

final case class AkkaMonitoringConfig(
  autoStart: AutoStartSettings,
  cleaning: CleaningSettings
)

final case class AutoStartSettings(
  akkaActor: Boolean,
  akkaHttp: Boolean,
  akkaPersistence: Boolean,
  akkaCluster: Boolean,
  akkaStream: Boolean,
  akkaDispatcher: Boolean
)

object AkkaMonitoringConfig extends MesmerConfiguration[AkkaMonitoringConfig] with Configuration {

  private val autoStartDefaults =
    AutoStartSettings(
      akkaActor = false,
      akkaHttp = false,
      akkaCluster = false,
      akkaPersistence = false,
      akkaStream = false,
      akkaDispatcher = false
    )
  private val cleaningSettingsDefaults = CleaningSettings(20.seconds, 5.second)

  /**
   * Name of configuration inside mesmer branch
   */
  protected val mesmerConfig: String = ""

  val defaultConfig: AkkaMonitoringConfig = AkkaMonitoringConfig(autoStartDefaults, cleaningSettingsDefaults)

  protected def extractFromConfig(monitoringConfig: Config): AkkaMonitoringConfig = {
    val autoStartSettings = monitoringConfig
      .tryValue("auto-start")(_.getConfig)
      .map { autoStartConfig =>
        val akkaActor = autoStartConfig.tryValue("akka-actor")(_.getBoolean).getOrElse(autoStartDefaults.akkaActor)
        val akkaHttp  = autoStartConfig.tryValue("akka-http")(_.getBoolean).getOrElse(autoStartDefaults.akkaHttp)
        val akkaPersistence =
          autoStartConfig.tryValue("akka-persistence")(_.getBoolean).getOrElse(autoStartDefaults.akkaPersistence)
        val akkaCluster =
          autoStartConfig.tryValue("akka-cluster")(_.getBoolean).getOrElse(autoStartDefaults.akkaCluster)
        val akkaStream =
          autoStartConfig.tryValue("akka-stream")(_.getBoolean).getOrElse(autoStartDefaults.akkaStream)
        val akkaDispatcher =
          autoStartConfig.tryValue("akka-dispatcher")(_.getBoolean).getOrElse(autoStartDefaults.akkaDispatcher)

        AutoStartSettings(akkaActor, akkaHttp, akkaPersistence, akkaCluster, akkaStream, akkaDispatcher)
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

}
