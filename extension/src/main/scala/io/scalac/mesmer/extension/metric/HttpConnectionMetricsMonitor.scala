package io.scalac.mesmer.extension.metric

import io.scalac.mesmer.core.AttributesSerializable
import io.scalac.mesmer.core.model.Interface
import io.scalac.mesmer.core.model.Node
import io.scalac.mesmer.core.model.Port
import io.scalac.mesmer.core.model.RawAttributes
import io.scalac.mesmer.core.module.AkkaHttpModule._

object HttpConnectionMetricsMonitor {

  final case class Attributes(node: Option[Node], interface: Interface, port: Port) extends AttributesSerializable {
    val serialize: RawAttributes = node.serialize ++ interface.serialize ++ port.serialize
  }

  trait BoundMonitor extends AkkaHttpConnectionsMetricsDef[Metric[Long]] with Synchronized with Bound {
    def connections: UpDownCounter[Long] with Instrument[Long]
  }

}
