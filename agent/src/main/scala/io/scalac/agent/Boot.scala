package io.scalac.agent

object Boot {

  def premain(args: String): Unit =
    AkkaPersistenceAgent.install()
}
