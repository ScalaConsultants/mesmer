package akka.stream

import akka.ConnectionOtelOps
import akka.stream.impl.fusing.GraphInterpreter.Connection
import net.bytebuddy.asm.Advice._
import scala.util.Try

object GraphInterpreterOtelPushAdvice {

  @OnMethodEnter
  def onPush(@Argument(0) currentConnection: Connection): Unit = {
    println("PUSHING")
    Try(ConnectionOtelOps.incrementPushCounter(currentConnection)).failed.foreach { ex =>
      ex.printStackTrace()
      println(s"Exception ${ex.getMessage}")
    }

  }

}

object GraphInterpreterOtelPullAdvice {

  @OnMethodEnter
  def onPull(@Argument(0) currentConnection: Connection): Unit = {
    println("PULLING")

    Try(ConnectionOtelOps.incrementPullCounter(currentConnection)).failed.foreach(ex =>
      println(s"Exception ${ex.getMessage}")
    )

  }

}
