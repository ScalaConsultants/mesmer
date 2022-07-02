package io.scalac.mesmer.agent.utils

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramPointData
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util

class OtelAgentHelpersTest extends AnyFlatSpec with Matchers {

  "OtelAgentHelpers.getBoundaryCountsWithToleration" should "count only 50-100 span" in {
    val point = ImmutableHistogramPointData.create(
      System.currentTimeMillis(),
      System.nanoTime(),
      Attributes.empty(),
      0L,
      0L,
      0L,
      util.Arrays.asList(50, 100, 200, 500),
      util.Arrays.asList(3, 5, 7, 13, 21)
    )

    val result = OtelAgentHelpers.getBoundaryCountsWithToleration(point, 90, 5)

    result should be(5)
  }

  it should "count both 50-100 and 100-200 span" in {
    val point = ImmutableHistogramPointData.create(
      System.currentTimeMillis(),
      System.nanoTime(),
      Attributes.empty(),
      0L,
      0L,
      0L,
      util.Arrays.asList(50, 100, 200, 500),
      util.Arrays.asList(3, 5, 7, 13, 21)
    )

    val result = OtelAgentHelpers.getBoundaryCountsWithToleration(point, 100, 49)

    result should be(12)
  }

  it should "count from all spans between 50 - 500" in {
    val point = ImmutableHistogramPointData.create(
      System.currentTimeMillis(),
      System.nanoTime(),
      Attributes.empty(),
      0L,
      0L,
      0L,
      util.Arrays.asList(5, 10, 20, 50, 75, 100, 150, 200, 500, 1000),
      util.Arrays.asList(0, 1, 1, 2, 3, 5, 8, 13, 21, 34, 55)
    )

    val result = OtelAgentHelpers.getBoundaryCountsWithToleration(point, 150, 90)

    result should be(50)
  }

}
