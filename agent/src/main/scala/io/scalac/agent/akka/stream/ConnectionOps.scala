package io.scalac.agent.akka.stream
import io.scalac.core.invoke.Lookup

object ConnectionOps extends Lookup {

  lazy val PullCounterVarName = "pullCounter"
  lazy val PushCounterVarName = "pushCounter"

  private val connectionClass = Class.forName("akka.stream.impl.fusing.GraphInterpreter$Connection")

  lazy val (pushHandleGetter, pushHandleSetter) = {
    val field = connectionClass.getDeclaredField(PushCounterVarName)
    field.setAccessible(true) // might not be necessary
    (lookup.unreflectGetter(field), lookup.unreflectSetter(field))
  }

  lazy val (pullHandleGetter, pullHandleSetter) = {
    val field = connectionClass.getDeclaredField(PullCounterVarName)
    field.setAccessible(true) // might not be necessary
    (lookup.unreflectGetter(field), lookup.unreflectSetter(field))
  }

  def incrementPushCounter(connection: AnyRef): Unit =
    pushHandleSetter.invoke(connection, pushHandleGetter.invoke(connection).asInstanceOf[Long] + 1)

  def incrementPullCounter(connection: AnyRef): Unit =
    pullHandleSetter.invoke(connection, pullHandleGetter.invoke(connection).asInstanceOf[Long] + 1)

  def getPushCounter(connection: AnyRef): Long =
    pushHandleGetter.invoke(connection).asInstanceOf[Long]

  /**
   * Use method handles to extract values stored in synthetic fields
   * @param connection
   * @return respectively push and pull counter values
   */
  def getAndResetCounterValues(connection: AnyRef): (Long, Long) = {
    val values =
      (pushHandleGetter.invoke(connection).asInstanceOf[Long], pullHandleGetter.invoke(connection).asInstanceOf[Long])
//    pullHandleSetter.invoke(connection, 0)
//    pushHandleSetter.invoke(connection, 0)
    values
  }

}
