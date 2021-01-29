package io.scalac.extension.upstream.opentelemetry

import io.opentelemetry.api.common.Labels
import io.opentelemetry.api.metrics.LongValueObserver

import io.scalac.extension.metric.MetricObserver

case class WrappedLongValueObserver(underlying: LongValueObserver.Builder, labels: Labels)
    extends MetricObserver[Long] {
  override def setUpdater(cb: MetricObserver.Result[Long] => Unit): Unit =
    underlying.setUpdater { underlyingResult =>
      cb { value =>
        println(s"calling callback function.... >>>> ${value} ${labels}")
        underlyingResult.observe(value, labels)
      }
    }.build() // registering
}
