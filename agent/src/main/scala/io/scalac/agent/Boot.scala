package io.scalac.agent

import net.bytebuddy.agent.ByteBuddyAgent

object Boot {

  def premain(args: String): Unit = {
    val agent = ByteBuddyAgent.install
    AkkaPersistenceAgent.install(agent)
    AkkaPersistenceAgent.transformEagerly()
  }
}
