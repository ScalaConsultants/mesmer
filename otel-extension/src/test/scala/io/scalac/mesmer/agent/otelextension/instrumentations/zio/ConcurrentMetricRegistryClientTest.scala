package io.scalac.mesmer.agent.otelextension.instrumentations.zio

import java.util.UUID

import io.scalac.mesmer.otelextension.instrumentations.zio.ConcurrentMetricRegistryClient
import io.scalac.mesmer.otelextension.instrumentations.zio.ConcurrentMetricRegistryClient.MetricHook
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import zio.metrics.MetricKey
import zio.metrics.MetricState

class ConcurrentMetricRegistryClientTest extends AnyWordSpec with Matchers {

  "ConcurrentMetricRegistryClient" should {
    "snapshot" in {
//      ConcurrentMetricRegistryClient
//        .snapshot() shouldBe empty
    }

    "get" in {
//      ConcurrentMetricRegistryClient
//        .get(MetricKey.counter(UUID.randomUUID().toString)) shouldBe a[MetricHook[Double, MetricState.Counter]]
    }
  }
}
