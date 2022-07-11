package io.scalac.mesmer.agent.utils

import io.opentelemetry.instrumentation.testing.AgentTestRunner
import io.opentelemetry.sdk.metrics.data.MetricData
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, OptionValues, TestSuite }
import org.scalatest.concurrent.Eventually

import scala.jdk.CollectionConverters.CollectionHasAsScala
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
   *   instrumentation name used for preliminary filtration
   * @param metricName
   *   metric name used for preliminary filtration
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

}
