package io.scalac.extension.config

import com.typesafe.config.Config
import io.scalac.extension.config

import scala.concurrent.duration._
import scala.jdk.DurationConverters._

case class ClusterMonitoringConfig(
  boot: BootSettings,
  autoStart: AutoStartSettings,
  backend: Option[BackendSettings],
  cleaningSettings: CleaningSettings
)

case class BootSettings(metricsBackend: Boolean)

case class AutoStartSettings(akkaHttp: Boolean, akkaPersistence: Boolean, akkaCluster: Boolean)

case class BackendSettings(name: String, region: String, apiKey: String, serviceName: String)

object ClusterMonitoringConfig {

  import config.ConfigurationUtils._

  private val autoStartDefaults        = AutoStartSettings(false, false, false)
  private val bootSettingsDefaults     = BootSettings(false)
  private val cleaningSettingsDefaults = CleaningSettings(20.seconds, 5.second)

  def apply(config: Config): ClusterMonitoringConfig =
    config
      .tryValue("io.scalac.akka-monitoring")(_.getConfig)
      .map { monitoringConfig =>
        val bootBackend =
          monitoringConfig
            .tryValue("boot.backend")(_.getBoolean)
            .getOrElse(bootSettingsDefaults.metricsBackend)

        val autoStartSettings = monitoringConfig
          .tryValue("auto-start")(_.getConfig)
          .map { autoStartConfig =>
            val akkaHttp = autoStartConfig.tryValue("akka-http")(_.getBoolean).getOrElse(autoStartDefaults.akkaHttp)
            val akkaPersistence =
              autoStartConfig.tryValue("akka-persistence")(_.getBoolean).getOrElse(autoStartDefaults.akkaPersistence)
            val akkaCluster =
              autoStartConfig.tryValue("akka-cluster")(_.getBoolean).getOrElse(autoStartDefaults.akkaCluster)
            AutoStartSettings(akkaHttp, akkaPersistence, akkaCluster)
          }
          .getOrElse(autoStartDefaults)

        val backend = monitoringConfig
          .tryValue("backend")(_.getConfig)
          .flatMap { backendConfig =>
            for {
              name        <- backendConfig.tryValue("name")(_.getString)
              apiKey      <- backendConfig.tryValue("api-key")(_.getString)
              region      <- backendConfig.tryValue("region")(_.getString)
              serviceName <- backendConfig.tryValue("service-name ")(_.getString)
            } yield BackendSettings(name, region, apiKey, serviceName)
          }
          .toOption

        val cleaningSettings = monitoringConfig
          .tryValue("cleaning")(_.getConfig)
          .flatMap { cleaningConfig =>
            for {
              max   <- cleaningConfig.tryValue("max-staleness")(_.getDuration)
              every <- cleaningConfig.tryValue("every")(_.getDuration)
            } yield CleaningSettings(max.toScala, every.toScala)
          }
          .getOrElse(cleaningSettingsDefaults)

        ClusterMonitoringConfig(
          BootSettings(bootBackend),
          autoStartSettings,
          backend,
          cleaningSettings
        )
      }
      .getOrElse(ClusterMonitoringConfig(bootSettingsDefaults, autoStartDefaults, None, cleaningSettingsDefaults))

}
