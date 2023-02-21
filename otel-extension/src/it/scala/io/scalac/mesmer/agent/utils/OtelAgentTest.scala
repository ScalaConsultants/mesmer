package io.scalac.mesmer.agent.utils

import io.opentelemetry.instrumentation.testing.AgentTestRunner
import io.opentelemetry.sdk.metrics.data.{ HistogramPointData, MetricData }
import io.opentelemetry.sdk.metrics.internal.aggregator.EmptyMetricData
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.must.Matchers.be
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, OptionValues, TestSuite }

import scala.jdk.CollectionConverters._
import scala.util.Try

trait OtelAgentTest extends TestSuite with BeforeAndAfterAll with Eventually with BeforeAndAfterEach with OptionValues {

  protected val testRunner = AgentTestRunner.instance()

  override protected def beforeAll(): Unit = testRunner.beforeTestClass()

  override protected def afterAll(): Unit = testRunner.afterTestClass()

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    testRunner.clearAllExportedData()
  }

  protected def assertMetric(metricName: String)(
    testFunction: MetricData => Unit
  ): Unit = assertMetrics("mesmer")(metricName -> testFunction)

  /**
   * @param instrumentationName
   *   intrumentation name used for preliminary filtration
   * @param tests
   *   metric name used for preliminary filtration
   * @param successOnEmpty
   *   whether test should be treated as success when no data was find for specified function
   * @param testFunction
   *   test function used the assert data. This will be executed once for every exported data (roughly every 100ms
   *   during test duration). It should return normally only when found data is correct.
   */
  protected def assertMetrics(
    instrumentationName: String
  )(tests: (String, MetricData => Unit)*): Unit =
    eventually {
      val exported = testRunner.getExportedMetrics.asScala

      val results = tests.map { case (metricName, test) =>
        val selectedMetrics = exported
          .filter(data => data.getInstrumentationScopeInfo.getName == instrumentationName && data.getName == metricName)

        val testData = if (selectedMetrics.isEmpty) EmptyMetricData.getInstance() :: Nil else selectedMetrics

        testData.map(data => Try(test.apply(data))).toList
      }

      val success = results.forall(_.exists(_.isSuccess))

      if (!success) {
        results.flatten
          .findLast(_.isFailure)
          .fold(fail("No matching data point found"))(_.failed.foreach(ex => throw ex))
      }

    }

  protected def getExpectedCountWithToleration(
    point: HistogramPointData,
    boundary: Double,
    toleration: Double = 20
  ): Long =
    OtelAgentHelpers.getExpectedCountWithToleration(point, boundary, toleration)

  protected def assertMetricIsCollected(metricName: String): Unit = assertMetricIsCollected("mesmer", metricName)

  protected def assertMetricIsCollected(instrumentationName: String, metricName: String): Unit =
    assertMetrics(instrumentationName)(metricName -> Function.const(()))

  protected def assertMetricSumGreaterOrEqualTo0(name: String): Unit =
    assertMetric(name) { data =>
      val totalCount = data.getLongSumData.getPoints.asScala.map(_.getValue).sum
      totalCount should be >= 0L
    }

}

object OtelAgentHelpers {

  /*
  This might make your test flaky! Remember to adjust toleration for CI.

  Here we calculate which bucket counts should we take into consideration, depending on boundary and
  toleration. Count in each bucket means that an action took LESS than this counter boundary. We
  take toleration into the mix to mitigate flakiness when things start to take longer than anticipated
   */
  private[utils] def getExpectedCountWithToleration(
    point: HistogramPointData,
    boundary: Double,
    toleration: Double = 20
  ): Long = {

    val lowestBoundary  = math.max(0.0, boundary - toleration)
    val highestBoundary = boundary + toleration

    val lowestIndex = point.getBoundaries.asScala.toVector.zipWithIndex.collectFirst {
      case (boundary, index) if boundary >= lowestBoundary => index
    }.getOrElse(0)

    val highestIndex = point.getBoundaries.asScala.toVector.zipWithIndex.collectFirst {
      case (boundary, index) if boundary >= highestBoundary => index
    }.getOrElse(point.getBoundaries.size() - 1)

    (lowestIndex to highestIndex).flatMap(point.getCounts.asScala.lift).map(_.longValue()).sum

  }

}
