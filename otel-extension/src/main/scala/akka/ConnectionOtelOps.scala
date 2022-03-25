package akka

import _root_.io.opentelemetry.instrumentation.api.field.VirtualField
import akka.stream.impl.fusing.GraphInterpreter.Connection

object ConnectionOtelOps {

  def incrementPushCounter(connection: Connection): Unit = {
    val (push, pull) = VirtualField
      .find(classOf[Connection], classOf[(Long, Long)])
      .get(connection)

    VirtualField
      .find(classOf[Connection], classOf[(Long, Long)])
      .set(connection, (push + 1, pull))

  }

  def incrementPullCounter(connection: Connection): Unit = {
    val (push, pull) = VirtualField
      .find(classOf[Connection], classOf[(Long, Long)])
      .get(connection)

    VirtualField
      .find(classOf[Connection], classOf[(Long, Long)])
      .set(connection, (push, pull + 1))

  }
  def getPushCounter(connection: Connection): Long = {
    val (push, _) = VirtualField
      .find(classOf[Connection], classOf[(Long, Long)])
      .get(connection)
    push
  }

  def getPullCounter(connection: Connection): Long = {
    val (_, pull) = VirtualField
      .find(classOf[Connection], classOf[(Long, Long)])
      .get(connection)
    pull
  }

  /**
   * Use method handles to extract values stored in synthetic fields
   * @param connection
   * @return
   *   respectively push and pull counter values
   */
  def getAndResetCounterValues(connection: Connection): (Long, Long) = {
    val (push, pull) = VirtualField
      .find(classOf[Connection], classOf[(Long, Long)])
      .get(connection)

    (push, pull)

  }

}
