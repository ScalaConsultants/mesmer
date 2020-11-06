package io.scalac.agent

import java.lang.instrument.Instrumentation

object Boot {

  def premain(args: String, instrumentation: Instrumentation): Unit = {
    AkkaPersistenceAgent.install(instrumentation)
    AkkaPersistenceAgent.transformEagerly()
  }
}
