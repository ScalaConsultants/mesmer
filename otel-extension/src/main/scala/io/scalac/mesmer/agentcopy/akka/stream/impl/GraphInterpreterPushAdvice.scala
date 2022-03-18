package io.scalac.mesmer.agentcopy.akka.stream.impl

import net.bytebuddy.asm.Advice._

import io.scalac.mesmer.agent.akka.stream.impl.ConnectionOps._

object GraphInterpreterPushAdvice {

  @OnMethodEnter
  def onPush(@Argument(0) currentConnection: AnyRef): Unit =
    ConnectionOps.incrementPushCounter(currentConnection)
}

object GraphInterpreterPullAdvice {

  @OnMethodEnter
  def onPull(@Argument(0) currentConnection: AnyRef): Unit =
    incrementPullCounter(currentConnection)
}
