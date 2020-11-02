package io.scalac.extension.config

import com.typesafe.config.Config
import io.scalac.extension.config

import scala.collection.JavaConverters._

case class ClusterMonitoringConfig(boot: BootSettings, regions: List[String])

case class BootSettings(bootMemberEvent: Boolean,
                        bootReachabilityEvents: Boolean)

object ClusterMonitoringConfig {

  import config.ConfigurationUtils._

  def apply(config: Config): ClusterMonitoringConfig = {

    config
      .tryValue("io.scalac.akka-cluster-monitoring")(_.getConfig)
      .map { monitoringConfig =>
        val bootMember =
          monitoringConfig.tryValue("boot.member")(_.getBoolean).getOrElse(true)

        val bootReachability =
          config.tryValue("boot.reachability")(_.getBoolean).getOrElse(true)

        val initRegions = monitoringConfig
          .tryValue("shard-regions")(
            config => path => config.getStringList(path).asScala.toList
          )
          .getOrElse(Nil)

        ClusterMonitoringConfig(
          BootSettings(bootMember, bootReachability),
          initRegions
        )
      }
      .getOrElse(ClusterMonitoringConfig(BootSettings(true, true), Nil))
  }

}
