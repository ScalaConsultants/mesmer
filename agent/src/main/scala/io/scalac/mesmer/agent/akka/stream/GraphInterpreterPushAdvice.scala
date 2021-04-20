package io.scalac.mesmer.agent.akka.stream
import net.bytebuddy.asm.Advice._

import io.scalac.mesmer.agent.akka.stream.ConnectionOps._

class GraphInterpreterPushAdvice

object GraphInterpreterPushAdvice {

  @OnMethodEnter
  def onPush(@Argument(0) currentConnection: AnyRef): Unit =
    ConnectionOps.incrementPushCounter(currentConnection)
}

class GraphInterpreterPullAdvice

object GraphInterpreterPullAdvice {

  @OnMethodEnter
  def onPull(@Argument(0) currentConnection: AnyRef): Unit =
    incrementPullCounter(currentConnection)
}
