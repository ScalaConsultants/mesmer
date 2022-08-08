package io.scalac.mesmer.agent.utils

import io.opentelemetry.instrumentation.testing.AgentTestRunner
import io.opentelemetry.sdk.metrics.data.{ HistogramPointData, MetricData }
import org.scalatest.concurrent.Eventually
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

  protected def assertMetrics(metricName: String, successOnEmpty: Boolean = false)(
    testFunction: PartialFunction[MetricData, Unit]
  ): Unit = assertMetrics("mesmer", metricName, successOnEmpty)(testFunction)

  /**
   * @param instrumentationName
   *   intrumentation name used for preliminary filtration
   * @param metricName
   *   metric name used for preliminary filtration
   * @param successOnEmpty
   *   whether test should be treated as success when no data was find for specified function
   * @param testFunction
   *   test function used the assert data. This will be executed once for every exported data (roughly every 100ms
   *   during test duration). It should return normally only when found data is correct.
   */
  protected def assertMetrics(instrumentationName: String, metricName: String, successOnEmpty: Boolean)(
    testFunction: PartialFunction[MetricData, Unit]
  ): Unit =
    eventually {
      val result = testRunner.getExportedMetrics.asScala
        .filter(data => data.getInstrumentationScopeInfo.getName == instrumentationName && data.getName == metricName)
        .collect {
          case data if testFunction.isDefinedAt(data) => Try(testFunction.apply(data))
        }

      if (!result.exists(_.isSuccess)) {
        result.lastOption
          .fold(if (!successOnEmpty) fail("No matching data point found"))(
            _.failed.foreach(ex => throw ex)
          ) // last series found is presented as an error
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
    assertMetrics(instrumentationName, metricName, false) { case _ => () }

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
