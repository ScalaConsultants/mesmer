package io.scalac.mesmer.agent.utils

import io.opentelemetry.context.ContextStorage
import io.opentelemetry.instrumentation.testing.AgentTestRunner
import io.opentelemetry.instrumentation.testing.util.ContextStorageCloser
import org.scalatest.BeforeAndAfterAll
import org.scalatest.BeforeAndAfterEach
import org.scalatest.TestSuite

abstract class OtelAgentTest extends TestSuite with BeforeAndAfterAll with BeforeAndAfterEach {

  private val testRunner = AgentTestRunner.instance()

  override protected def beforeAll(): Unit = testRunner.beforeTestClass()

  override protected def afterAll(): Unit = testRunner.afterTestClass()

  override protected def beforeEach(): Unit = testRunner.clearAllExportedData()

  override protected def afterEach(): Unit = ContextStorageCloser.close(ContextStorage.get)
}
