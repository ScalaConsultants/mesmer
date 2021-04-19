package io.scalac.mesmer.extension.metric

import io.scalac.mesmer.core.LabelSerializable
import io.scalac.mesmer.core.model.Interface
import io.scalac.mesmer.core.model.Node
import io.scalac.mesmer.core.model.Port
import io.scalac.mesmer.core.model.RawLabels

object HttpConnectionMetricsMonitor {

  final case class Labels(node: Option[Node], interface: Interface, port: Port) extends LabelSerializable {
    val serialize: RawLabels = node.serialize ++ interface.serialize ++ port.serialize
  }

  trait BoundMonitor extends Synchronized with Bound {
    def connectionCounter: UpDownCounter[Long] with Instrument[Long]
  }

}
