package io.scalac.mesmer.agent.akka.stream.impl

import io.scalac.mesmer.agent.akka.stream.impl.ConnectionOps._
import net.bytebuddy.asm.Advice._

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
