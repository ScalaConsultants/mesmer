package io.scalac.mesmer.extension.upstream

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.scalac.mesmer.core.module.AkkaActorModule
import io.scalac.mesmer.extension.metric.MetricObserver
import io.scalac.mesmer.extension.upstream.opentelemetry.WrappedMetricObserver
import io.scalac.mesmer.extension.util.OpenTelemetryNoopMeter

class OpenTelemetryActorMetricsMonitorTest extends AnyFlatSpec with Matchers {

  behavior of "OpenTelemetryActorMetricsMonitor"

  it should "bind to OpenTelemetry instruments if metric is enables" in {
    val allEnabled = AkkaActorModule.Impl(
      mailboxSize = true,
      mailboxTimeMin = true,
      mailboxTimeMax = true,
      mailboxTimeSum = true,
      mailboxTimeCount = true,
      stashedMessages = true,
      receivedMessages = true,
      processedMessages = true,
      failedMessages = true,
      processingTimeMin = true,
      processingTimeMax = true,
      processingTimeSum = true,
      processingTimeCount = true,
      sentMessages = true,
      droppedMessages = true
    )
    val sut = new OpenTelemetryActorMetricsMonitor(
      OpenTelemetryNoopMeter.instance,
      allEnabled,
      OpenTelemetryActorMetricsMonitor.MetricNames.defaultConfig
    )

    val monitor = sut.bind()

    monitor.mailboxSize should be(a[WrappedMetricObserver[_, _]])
    monitor.mailboxTimeMin should be(a[WrappedMetricObserver[_, _]])
    monitor.mailboxTimeMax should be(a[WrappedMetricObserver[_, _]])
    monitor.mailboxTimeSum should be(a[WrappedMetricObserver[_, _]])
    monitor.mailboxTimeCount should be(a[WrappedMetricObserver[_, _]])
    monitor.stashedMessages should be(a[WrappedMetricObserver[_, _]])
    monitor.receivedMessages should be(a[WrappedMetricObserver[_, _]])
    monitor.processedMessages should be(a[WrappedMetricObserver[_, _]])
    monitor.failedMessages should be(a[WrappedMetricObserver[_, _]])
    monitor.processingTimeMin should be(a[WrappedMetricObserver[_, _]])
    monitor.processingTimeMax should be(a[WrappedMetricObserver[_, _]])
    monitor.processingTimeSum should be(a[WrappedMetricObserver[_, _]])
    monitor.processingTimeCount should be(a[WrappedMetricObserver[_, _]])
    monitor.sentMessages should be(a[WrappedMetricObserver[_, _]])
    monitor.droppedMessages should be(a[WrappedMetricObserver[_, _]])
  }

  it should "bind to noop instruments if metric is disabled" in {
    val allEnabled = AkkaActorModule.Impl(
      mailboxSize = false,
      mailboxTimeCount = false,
      mailboxTimeMin = false,
      mailboxTimeMax = false,
      mailboxTimeSum = false,
      stashedMessages = false,
      receivedMessages = false,
      processedMessages = false,
      failedMessages = false,
      processingTimeCount = false,
      processingTimeMin = false,
      processingTimeMax = false,
      processingTimeSum = false,
      sentMessages = false,
      droppedMessages = false
    )
    val sut = new OpenTelemetryActorMetricsMonitor(
      OpenTelemetryNoopMeter.instance,
      allEnabled,
      OpenTelemetryActorMetricsMonitor.MetricNames.defaultConfig
    )

    val monitor = sut.bind()

    monitor.mailboxSize should be(a[MetricObserver.NoopMetricObserver.type])
    monitor.mailboxTimeMin should be(a[MetricObserver.NoopMetricObserver.type])
    monitor.mailboxTimeMax should be(a[MetricObserver.NoopMetricObserver.type])
    monitor.mailboxTimeSum should be(a[MetricObserver.NoopMetricObserver.type])
    monitor.mailboxTimeCount should be(a[MetricObserver.NoopMetricObserver.type])
    monitor.stashedMessages should be(a[MetricObserver.NoopMetricObserver.type])
    monitor.receivedMessages should be(a[MetricObserver.NoopMetricObserver.type])
    monitor.processedMessages should be(a[MetricObserver.NoopMetricObserver.type])
    monitor.failedMessages should be(a[MetricObserver.NoopMetricObserver.type])
    monitor.processingTimeMin should be(a[MetricObserver.NoopMetricObserver.type])
    monitor.processingTimeMax should be(a[MetricObserver.NoopMetricObserver.type])
    monitor.processingTimeSum should be(a[MetricObserver.NoopMetricObserver.type])
    monitor.processingTimeCount should be(a[MetricObserver.NoopMetricObserver.type])
    monitor.sentMessages should be(a[MetricObserver.NoopMetricObserver.type])
    monitor.droppedMessages should be(a[MetricObserver.NoopMetricObserver.type])
  }
}
