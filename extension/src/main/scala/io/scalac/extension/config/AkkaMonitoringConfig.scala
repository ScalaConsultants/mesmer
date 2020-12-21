package io.scalac.extension.config

import com.typesafe.config.Config
import io.scalac.extension.config

case class ClusterMonitoringConfig(boot: BootSettings, autoStart: AutoStartSettings, backend: Option[BackendSettings])

case class BootSettings(metricsBackend: Boolean)

case class AutoStartSettings(akkaHttp: Boolean, akkaPersistence: Boolean, akkaCluster: Boolean)

case class BackendSettings(name: String, region: String, apiKey: String, serviceName: String)

object ClusterMonitoringConfig {

  import config.ConfigurationUtils._

  private val autoStartDefaults    = AutoStartSettings(false, false, false)
  private val bootSettingsDefaults = BootSettings(false)

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

        ClusterMonitoringConfig(
          BootSettings(bootBackend),
          autoStartSettings,
          backend
        )
      }
      .getOrElse(ClusterMonitoringConfig(bootSettingsDefaults, autoStartDefaults, None))

}
