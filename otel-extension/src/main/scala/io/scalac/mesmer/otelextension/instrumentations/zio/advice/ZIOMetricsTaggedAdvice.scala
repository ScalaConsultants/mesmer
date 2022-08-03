package io.scalac.mesmer.otelextension.instrumentations.zio.advice

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.ObservableDoubleCounter
import io.opentelemetry.api.metrics.ObservableDoubleGauge
import io.opentelemetry.instrumentation.api.field.VirtualField
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
    Option(
      VirtualField
        .find(classOf[Metric[Type, _, _]], classOf[AutoCloseable])
        .get(oldMetric)
    ).foreach { instrument: AutoCloseable => instrument.close() }

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
        val newOtelCounter: ObservableDoubleCounter =
          registerCounterAsyncMetric(name, newMetric.asInstanceOf[Counter[_]], newAttributes)

        VirtualField
          .find(classOf[Metric[Type, _, _]], classOf[ObservableDoubleCounter])
          .set(newMetric, newOtelCounter)

      case _: MetricKeyType.Gauge =>
        val newOtelGauge = registerGaugeAsyncMetric(name, newMetric.asInstanceOf[Gauge[_]], newAttributes)

        VirtualField
          .find(classOf[Metric[Type, _, _]], classOf[ObservableDoubleGauge])
          .set(newMetric, newOtelGauge)

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
