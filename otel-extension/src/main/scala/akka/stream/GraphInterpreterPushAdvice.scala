package akka.stream

import akka.ConnectionOtelOps
import akka.stream.impl.fusing.GraphInterpreter.Connection
import net.bytebuddy.asm.Advice._

object GraphInterpreterOtelPushAdvice {

  @OnMethodEnter
  def onPush(@Argument(0) currentConnection: Connection): Unit =
    ConnectionOtelOps.incrementPushCounter(currentConnection)

}

object GraphInterpreterOtelPullAdvice {

  @OnMethodEnter
  def onPull(@Argument(0) currentConnection: Connection): Unit =
    ConnectionOtelOps.incrementPullCounter(currentConnection)

}
