package io.scalac.mesmer.extension.config

import com.typesafe.config.Config

import io.scalac.mesmer.core.config.Configuration
import io.scalac.mesmer.core.config.MesmerConfiguration

final case class AkkaMonitoringConfig(
  autoStart: AutoStartSettings
)

final case class AutoStartSettings(
  akkaCluster: Boolean,
  akkaStream: Boolean
)

object AkkaMonitoringConfig extends MesmerConfiguration[AkkaMonitoringConfig] with Configuration {

  private val autoStartDefaults =
    AutoStartSettings(
      akkaCluster = false,
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
        val akkaCluster =
          autoStartConfig.tryValue("akka-cluster")(_.getBoolean).getOrElse(autoStartDefaults.akkaCluster)
        val akkaStream =
          autoStartConfig.tryValue("akka-stream")(_.getBoolean).getOrElse(autoStartDefaults.akkaStream)

        AutoStartSettings(akkaCluster, akkaStream)
      }
      .getOrElse(autoStartDefaults)

    AkkaMonitoringConfig(
      autoStartSettings
    )
  }

}
