package akka.stream

import akka.ConnectionOtelOps
import akka.stream.impl.fusing.GraphInterpreter.Connection
import net.bytebuddy.asm.Advice._

object GraphInterpreterOtelPushAdvice {

  // See the summary explaining why the "Any" argument type is used:
  // https://github.com/ScalaConsultants/mesmer/pull/361
  @OnMethodEnter
  def onPush(@Argument(0) currentConnection: Any): Unit =
    ConnectionOtelOps.incrementPushCounter(currentConnection.asInstanceOf[Connection])

}

object GraphInterpreterOtelPullAdvice {

  // See the summary explaining why the "Any" argument type is used:
  // https://github.com/ScalaConsultants/mesmer/pull/361
  @OnMethodEnter
  def onPull(@Argument(0) currentConnection: Any): Unit =
    ConnectionOtelOps.incrementPullCounter(currentConnection.asInstanceOf[Connection])

}
