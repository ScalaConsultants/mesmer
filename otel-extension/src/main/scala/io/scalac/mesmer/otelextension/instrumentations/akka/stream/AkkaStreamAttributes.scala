package io.scalac.mesmer.otelextension.instrumentations.akka.stream

import io.opentelemetry.api.common.Attributes

import io.scalac.mesmer.core.model.Node

object AkkaStreamAttributes {
  def forNode(node: Option[Node]): Attributes =
    node.foldLeft(Attributes.builder())(_.put("node", _)).build
}
