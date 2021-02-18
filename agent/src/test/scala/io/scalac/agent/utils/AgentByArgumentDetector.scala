package io.scalac.agent.utils

import scala.jdk.CollectionConverters._

object AgentByArgumentDetector {

  def isPresent: Boolean =
    management.ManagementFactory.getRuntimeMXBean.getInputArguments.asScala.exists(arg =>
      (arg.startsWith("-javaagent") || arg.startsWith("-agentlib") || arg.startsWith("-agentpath"))
        && arg.contains("scalac") && arg.contains("agent")
    )

}
