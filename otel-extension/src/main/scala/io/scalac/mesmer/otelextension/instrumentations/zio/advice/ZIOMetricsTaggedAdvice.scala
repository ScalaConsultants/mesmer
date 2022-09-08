package io.scalac.mesmer.otelextension.instrumentations.zio.advice

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.instrumentation.api.util.VirtualField
import net.bytebuddy.asm.Advice
import zio.metrics.Metric
import zio.metrics.Metric.Counter
import zio.metrics.Metric.Gauge
import zio.metrics.MetricKeyType
import zio.metrics.MetricLabel

import io.scalac.mesmer.otelextension.instrumentations.zio.ZIOMetrics._

object ZIOMetricsTaggedAdvice {

  @Advice.OnMethodExit
  def tagged[Type <: MetricKeyType, _](
    @Advice.Argument(0) extraTags: Set[MetricLabel],
    @Advice.This oldMetric: Metric[Type, _, _],
    @Advice.Return newMetric: Metric[Type, _, _]
  ): Unit = {
    val name: String = VirtualField
      .find(classOf[Metric[Type, _, _]], classOf[String])
      .get(oldMetric)

    val attributesSoFar: Option[Attributes] = Option(
      VirtualField
        .find(classOf[Metric[Type, _, _]], classOf[Attributes])
        .get(oldMetric)
    )

    val extraAttributes: Attributes = buildAttributes(extraTags)

    val newAttributes = Attributes
      .builder()
      .putAll(attributesSoFar.getOrElse(Attributes.empty()))
      .putAll(extraAttributes)
      .build()

    oldMetric.keyType match {
      case _: MetricKeyType.Counter =>
        registerCounterAsyncMetric(name, newMetric.asInstanceOf[Counter[_]], newAttributes)
      case _: MetricKeyType.Gauge =>
        registerGaugeAsyncMetric(name, newMetric.asInstanceOf[Gauge[_]], newAttributes)
      case _ => ()
    }

    VirtualField
      .find(classOf[Metric[Type, _, _]], classOf[Attributes])
      .set(newMetric, newAttributes)

    VirtualField
      .find(classOf[Metric[Type, _, _]], classOf[String])
      .set(newMetric, name)
  }
}
