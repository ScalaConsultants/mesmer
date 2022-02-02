package io.scalac.mesmer.extension.opentelemetry

import io.opentelemetry.api.OpenTelemetry
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.scalac.mesmer.extension.opentelemetry.OpenTelemetryLoader.OpenTelemetryConfigurationException

class OpenTelemetryLoaderTest extends AnyFlatSpec with Matchers {

  case class TestProvider() extends OpenTelemetryProvider {
    override def create(): OpenTelemetry = throw new RuntimeException("I should not be called!")
  }

  it should "allow only one Open Telemetry configuration" in {
    val loadedInstances: Seq[OpenTelemetryProvider] = TestProvider() :: TestProvider() :: Nil

    val loader: OpenTelemetryLoader = OpenTelemetryLoader.make(() => loadedInstances.iterator)

    assertThrows[OpenTelemetryConfigurationException] {
      loader.load()
    }
  }

  it should "throw exception when there's no configuration" in {
    val loadedInstances: Seq[OpenTelemetryProvider] = Nil

    val loader: OpenTelemetryLoader = OpenTelemetryLoader.make(() => loadedInstances.iterator)

    assertThrows[OpenTelemetryConfigurationException] {
      loader.load()
    }
  }

  it should "successfully return the sole provider instance" in {
    val loadedInstances: Seq[OpenTelemetryProvider] = TestProvider() :: Nil

    val loader: OpenTelemetryLoader = OpenTelemetryLoader.make(() => loadedInstances.iterator)

    val provider: OpenTelemetryProvider = loader.load()

    provider shouldBe a[TestProvider]
  }
}
