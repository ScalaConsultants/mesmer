package io.scalac.agent.utils

import scala.jdk.CollectionConverters._

object AgentDetector {

  def ifAgentIsNotPresent(block: => Unit): Unit =
    if (!isAgentPresent) block

  def isAgentPresent: Boolean =
    management.ManagementFactory.getRuntimeMXBean.getInputArguments.asScala.exists(arg =>
      arg.startsWith("-javaagent:") && arg.contains("scalac_agent.jar")
    )

}
