package io.scalac.extension.metric

import io.scalac.core.LabelSerializable
import io.scalac.core.model.Interface
import io.scalac.core.model.Node
import io.scalac.core.model.Port
import io.scalac.core.model.RawLabels

object HttpConnectionMetricMonitor {

  final case class Labels(node: Option[Node], interface: Interface, port: Port) extends LabelSerializable {
    override val serialize: RawLabels = node.serialize ++ interface.serialize ++ port.serialize
  }

  trait BoundMonitor extends Synchronized with Bound {
    def connectionCounter: UpDownCounter[Long] with Instrument[Long]
  }

}
