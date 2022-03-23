package akka.stream

import akka.ConnectionOtelOps
import akka.stream.impl.fusing.GraphInterpreter.Connection
import net.bytebuddy.asm.Advice._
import scala.util.Try

object GraphInterpreterOtelPushAdvice {

  @OnMethodEnter
  def onPush(@Argument(0) currentConnection: Connection): Unit = {
    Try {
      ConnectionOtelOps.incrementPushCounter(currentConnection)
    }.failed.foreach { exception =>
      exception.printStackTrace()
      throw exception
    }
  }

//  @OnMethodExit
//  def checkFeilure(@Thrown exception: Throwable): Unit =  {
//      if(exception ne null) {
//      }
//  }
}

object GraphInterpreterOtelPullAdvice {

//  def onPull(@Argument(0) currentConnection: Connection): Unit = {
  @OnMethodEnter
  def onPull(@Argument(0) currentConnection: Connection): Unit = {
    Try {
      ConnectionOtelOps.incrementPullCounter(currentConnection)
    }.failed.foreach { exception =>
      println(s"Exception when pulling a connection: ${exception.getMessage}")
//      exception.printStackTrace()
    }

  }

//  @OnMethodExit
//  def checkFeilure(@Thrown exception: Throwable): Unit =  {
//    if(exception ne null) {
//      println(s"Exception when pulling a connection: ${exception.getMessage}")
//    }
//  }
}
