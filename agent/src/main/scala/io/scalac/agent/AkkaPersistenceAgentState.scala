package io.scalac.agent

import java.util.concurrent.ConcurrentHashMap

object AkkaPersistenceAgentState {
  var recoveryStarted: java.util.Map[String, Long]      = new ConcurrentHashMap[String, Long](100)
  var recoveryMeasurements: java.util.Map[String, Long] = new ConcurrentHashMap[String, Long](100)
}
