package io.scalac.agent.akka.stream
import io.scalac.agent.akka.stream.ConnectionOps._
import net.bytebuddy.asm.Advice._

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
