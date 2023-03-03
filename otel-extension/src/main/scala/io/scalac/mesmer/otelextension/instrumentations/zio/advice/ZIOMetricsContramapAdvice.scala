package io.scalac.mesmer.otelextension.instrumentations.zio.advice

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.instrumentation.api.util.VirtualField
import net.bytebuddy.asm.Advice
import zio.metrics.Metric
import zio.metrics.MetricKeyType

object ZIOMetricsContramapAdvice {

  @Advice.OnMethodExit
  def contramap[Type <: MetricKeyType](
    @Advice.This oldMetric: Metric[Type, _, _],
    @Advice.Return newMetric: Metric[Type, _, _]
  ): Unit = {
    val name: String = VirtualField
      .find(classOf[Metric[Type, _, _]], classOf[String])
      .get(oldMetric)

    val attributes: Attributes = VirtualField
      .find(classOf[Metric[Type, _, _]], classOf[Attributes])
      .get(oldMetric)

    VirtualField
      .find(classOf[Metric[Type, _, _]], classOf[String])
      .set(newMetric, name)

    VirtualField
      .find(classOf[Metric[Type, _, _]], classOf[Attributes])
      .set(newMetric, attributes)
  }

}
