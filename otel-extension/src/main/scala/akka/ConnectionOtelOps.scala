package akka

import _root_.io.opentelemetry.instrumentation.api.field.VirtualField
import _root_.io.scalac.mesmer.otelextension.instrumentations.akka.stream.impl.ConnectionCounters
import akka.stream.impl.fusing.GraphInterpreter.Connection

object ConnectionOtelOps {

  def incrementPushCounter(connection: Connection): Unit = {
    val counters: ConnectionCounters = VirtualField
      .find(classOf[Connection], classOf[ConnectionCounters])
      .get(connection)

    counters.incrementPush
  }

  def incrementPullCounter(connection: Connection): Unit = {
    val counters: ConnectionCounters = VirtualField
      .find(classOf[Connection], classOf[ConnectionCounters])
      .get(connection)

    counters.incrementPull
  }

  /**
   * Use method handles to extract values stored in synthetic fields
   * @param connection
   * @return
   *   respectively push and pull counter values
   */
  def getCounterValues(connection: Connection): (Long, Long) = {
    val counters: ConnectionCounters = VirtualField
      .find(classOf[Connection], classOf[ConnectionCounters])
      .get(connection)

    counters.getCounters
  }

}
