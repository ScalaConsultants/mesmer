package io.scalac.agent

import java.util.concurrent.ConcurrentHashMap

object AkkaPersistenceAgentState {
  val recoveryStarted: java.util.Map[String, Long]      = new ConcurrentHashMap[String, Long](100)
  val recoveryMeasurements: java.util.Map[String, Long] = new ConcurrentHashMap[String, Long](100)
}
