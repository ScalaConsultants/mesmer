package io.scalac.extension.metric

import io.scalac.core.LabelSerializable
import io.scalac.core.model._

object HttpMetricMonitor {

  sealed trait Labels extends LabelSerializable

  final case class RequestLabels(node: Option[Node], path: Path, method: Method, status: Status) extends Labels {
    override val serialize: RawLabels = node.serialize ++ path.serialize ++ method.serialize ++ status.serialize
  }

  final case class ConnectionLabels(node: Option[Node], interface: Interface, port: Port) extends Labels {
    override val serialize: RawLabels = node.serialize ++ interface.serialize ++ port.serialize
  }

  trait BoundMonitor extends Synchronized with Bound {
    def connectionCounter: UpDownCounter[Long] with Instrument[Long]
    def requestTime: MetricRecorder[Long] with Instrument[Long]
    def requestCounter: Counter[Long] with Instrument[Long]
  }

}
