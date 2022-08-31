package io.scalac.mesmer.extension.config

import com.typesafe.config.Config

import io.scalac.mesmer.core.config.Configuration
import io.scalac.mesmer.core.config.MesmerConfiguration

final case class AkkaMonitoringConfig(
  autoStart: AutoStartSettings
)

final case class AutoStartSettings(
  akkaActor: Boolean,
  akkaPersistence: Boolean,
  akkaCluster: Boolean,
  akkaStream: Boolean
)

object AkkaMonitoringConfig extends MesmerConfiguration[AkkaMonitoringConfig] with Configuration {

  private val autoStartDefaults =
    AutoStartSettings(
      akkaActor = false,
      akkaCluster = false,
      akkaPersistence = false,
      akkaStream = false
    )

  /**
   * Name of configuration inside mesmer branch
   */
  protected val mesmerConfig: String = ""

  val defaultConfig: AkkaMonitoringConfig = AkkaMonitoringConfig(autoStartDefaults)

  protected def extractFromConfig(monitoringConfig: Config): AkkaMonitoringConfig = {
    val autoStartSettings = monitoringConfig
      .tryValue("auto-start")(_.getConfig)
      .map { autoStartConfig =>
        val akkaActor = autoStartConfig.tryValue("akka-actor")(_.getBoolean).getOrElse(autoStartDefaults.akkaActor)
        val akkaPersistence =
          autoStartConfig.tryValue("akka-persistence")(_.getBoolean).getOrElse(autoStartDefaults.akkaPersistence)
        val akkaCluster =
          autoStartConfig.tryValue("akka-cluster")(_.getBoolean).getOrElse(autoStartDefaults.akkaCluster)
        val akkaStream =
          autoStartConfig.tryValue("akka-stream")(_.getBoolean).getOrElse(autoStartDefaults.akkaStream)

        AutoStartSettings(akkaActor, akkaPersistence, akkaCluster, akkaStream)
      }
      .getOrElse(autoStartDefaults)

    AkkaMonitoringConfig(
      autoStartSettings
    )
  }

}
