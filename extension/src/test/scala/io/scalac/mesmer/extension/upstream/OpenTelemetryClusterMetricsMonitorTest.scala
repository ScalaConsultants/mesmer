package io.scalac.mesmer.extension.upstream

import io.opentelemetry.api.metrics.OpenTelemetryNoopMeter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.scalac.mesmer.core.module.AkkaClusterModule
import io.scalac.mesmer.extension.metric.ClusterMetricsMonitor.Attributes
import io.scalac.mesmer.extension.metric.MetricObserver.NoopMetricObserver
import io.scalac.mesmer.extension.upstream.opentelemetry._

class OpenTelemetryClusterMetricsMonitorTest extends AnyFlatSpec with Matchers {

  behavior of "OpenTelemetryClusterMetricsMonitor"

  val TestLabels: Attributes = Attributes("some-node", None)

  private def config(value: Boolean) = AkkaClusterModule.Impl(
    shardPerRegions = value,
    entityPerRegion = value,
    shardRegionsOnNode = value,
    entitiesOnNode = value,
    reachableNodes = value,
    unreachableNodes = value,
    nodeDown = value
  )

  it should "bind to OpenTelemetry instruments if metric is enabled" in {
    val sut = new OpenTelemetryClusterMetricsMonitor(
      OpenTelemetryNoopMeter.instance,
      config(true),
      OpenTelemetryClusterMetricsMonitor.MetricNames.defaultConfig
    )

    val bound = sut.bind(TestLabels)

    bound.shardPerRegions should be(a[WrappedMetricObserver[_, _]])
    bound.entityPerRegion should be(a[WrappedMetricObserver[_, _]])
    bound.shardRegionsOnNode should be(a[WrappedMetricObserver[_, _]])
    bound.entitiesOnNode should be(a[WrappedMetricObserver[_, _]])
    bound.reachableNodes should be(a[WrappedUpDownCounter])
    bound.unreachableNodes should be(a[WrappedUpDownCounter])
    bound.nodeDown should be(a[WrappedCounter])

  }

  it should "bind to noop instruments if metric is disabled" in {
    val sut = new OpenTelemetryClusterMetricsMonitor(
      OpenTelemetryNoopMeter.instance,
      config(false),
      OpenTelemetryClusterMetricsMonitor.MetricNames.defaultConfig
    )

    val bound = sut.bind(TestLabels)
    bound.shardPerRegions should be(a[NoopMetricObserver.type])
    bound.entityPerRegion should be(a[NoopMetricObserver.type])
    bound.shardRegionsOnNode should be(a[NoopMetricObserver.type])
    bound.entitiesOnNode should be(a[NoopMetricObserver.type])
    bound.reachableNodes should be(a[NoopUpDownCounter.type])
    bound.unreachableNodes should be(a[NoopUpDownCounter.type])
    bound.nodeDown should be(a[NoopCounter.type])
  }
}
