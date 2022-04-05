package akka.stream

import akka.ConnectionOtelOps
import akka.stream.impl.fusing.GraphInterpreter.Connection
import net.bytebuddy.asm.Advice._

object GraphInterpreterOtelPushAdvice {

  @OnMethodEnter
  def onPush(@Argument(0) currentConnection: Any): Unit =
    ConnectionOtelOps.incrementPushCounter(currentConnection.asInstanceOf[Connection])

}

object GraphInterpreterOtelPullAdvice {

  @OnMethodEnter
  def onPull(@Argument(0) currentConnection: Any): Unit =
    ConnectionOtelOps.incrementPullCounter(currentConnection.asInstanceOf[Connection])

}
