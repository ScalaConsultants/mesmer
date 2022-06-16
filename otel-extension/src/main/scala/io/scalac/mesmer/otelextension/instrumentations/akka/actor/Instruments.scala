package io.scalac.mesmer.otelextension.instrumentations.akka.actor

import akka.actor.ActorSystem
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongHistogram
import io.opentelemetry.api.metrics.MeterProvider
import io.opentelemetry.instrumentation.api.field.VirtualField
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.`export`.MetricReader

import io.scalac.mesmer.core.actor.ActorRefConfiguration

// TODO can we add resources here to automatically inject actor system tags?
final class Instruments(val config: ActorRefConfiguration, provider: MeterProvider) {

  lazy val failedMessages: LongCounter = provider
    .get("mesmer")
    .counterBuilder("mesmer_akka_failed_messages")
    .build()

  lazy val processingTime: LongHistogram = provider
    .get("mesmer")
    .histogramBuilder("mesmer_akka_processing_time")
    .ofLongs()
    .build()

  def printVF(): Unit = {
    println(s"FROM ADVICE ${classOf[VirtualField[_, _]].getName}")
    println(s"FROM ADVICE ${VirtualField.find(classOf[ActorSystem], classOf[Instruments]).getClass.getName}")
  }
}

object Instruments {

  // TODO this should be inside test package - we should not inject this class into users jars
  // this is needed to test ActorSystem appropriately, but should not reference SDK directly
  def setUpMetricReader(metricReader: MetricReader, system: ActorSystem): Unit = {

    val meterProvider = SdkMeterProvider.builder().registerMetricReader(metricReader).build()
    val virtualField: VirtualField[ActorSystem, Instruments] = VirtualField
      .find(classOf[ActorSystem], classOf[Instruments])

    val instruments: Instruments = virtualField
      .get(system)

    VirtualField
      .find(classOf[ActorSystem], classOf[Instruments])
      .set(system, new Instruments(instruments.config, meterProvider))
  }
}
