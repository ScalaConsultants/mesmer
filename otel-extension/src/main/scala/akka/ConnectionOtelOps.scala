package akka

import _root_.io.opentelemetry.instrumentation.api.field.VirtualField
import _root_.io.scalac.mesmer.otelextension.instrumentations.akka.stream.impl.ConnectionCounters
import akka.stream.impl.fusing.GraphInterpreter.Connection

object ConnectionOtelOps {

  def incrementPushCounter(connection: Connection): Unit = {
    VirtualField
      .find(classOf[Connection], classOf[ConnectionCounters])
      .get(connection)
      .incrementPush
  }

  def incrementPullCounter(connection: Connection): Unit = {
    VirtualField
      .find(classOf[Connection], classOf[ConnectionCounters])
      .get(connection)
      .incrementPull
  }

  /**
   * Use method handles to extract values stored in synthetic fields
   * @param connection
   * @return
   *   respectively push and pull counter values
   */
  def getCounterValues(connection: Connection): (Long, Long) =
    VirtualField
      .find(classOf[Connection], classOf[ConnectionCounters])
      .get(connection)
      .getCounters

}
