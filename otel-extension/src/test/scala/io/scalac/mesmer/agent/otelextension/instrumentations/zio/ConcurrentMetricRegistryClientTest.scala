package io.scalac.mesmer.agent.otelextension.instrumentations.zio

import java.util.UUID

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import zio.metrics.MetricKey

import io.scalac.mesmer.otelextension.instrumentations.zio.ConcurrentMetricRegistryClient
import io.scalac.mesmer.otelextension.instrumentations.zio.ConcurrentMetricRegistryClient.MetricHook
import io.scalac.mesmer.otelextension.instrumentations.zio.ConcurrentMetricRegistryClient.MetricListener

class ConcurrentMetricRegistryClientTest extends AnyWordSpec with Matchers {

  private lazy val client = new ConcurrentMetricRegistryClient(
    {
      val packageObject = Class.forName("zio.internal.metrics.package$")
      val method        = packageObject.getMethod("metricRegistry")
      val module        = packageObject.getField("MODULE$").get(null)
      method.invoke(module)
    }
  )

  "ConcurrentMetricRegistryClient" should {
    "snapshot" in {
      client
        .snapshot() shouldBe empty
    }

    "get" in {
      client
        .get(MetricKey.counter(UUID.randomUUID().toString)) shouldBe a[MetricHook[_, _]]
    }

    "add listener" in {
      client
        .addListener(new MetricListener {}) shouldBe ()
    }
  }
}
