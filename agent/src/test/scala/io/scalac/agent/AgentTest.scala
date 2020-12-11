package io.scalac.agent

import java.lang.instrument.Instrumentation

import io.scalac.agent.Agent.LoadingResult
import io.scalac.agent.model._
import io.scalac.agent.util.ModuleInfo.Modules
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AgentTest extends AnyFlatSpec with Matchers {

  val testModuleOne             = Module("test-module-1")
  val testModuleTwo             = Module("test-module-2")
  val moduleOneVersion: Version = Version(2, 2, 2)
  val moduleTwoVersion: Version = Version(3, 3, 3)
  val modules: Modules          = Map(testModuleOne -> moduleOneVersion, testModuleTwo -> moduleTwoVersion)

  type Fixture = (Instrumentation, AgentBuilder)

  def test(body: Fixture => Any): Any = {

    val instrumentation = ByteBuddyAgent.install()
    val builder         = new AgentBuilder.Default()
    Function.untupled(body)(instrumentation, builder)
  }

  def returning(result: LoadingResult): (AgentBuilder, Instrumentation, Modules) => LoadingResult = (_, _, _) => result

  "Agent" should "execute instrumenting function when version match" in test {
    case (instrumentation, builder) =>
      val supportedModules = modules.foldLeft(SupportedModules.empty) {
        case (supported, (module, version)) => supported ++ (module, SupportedVersion(version))
      }
      val expectedResult = LoadingResult("some.class", "other.class")
      val sut            = Agent(AgentInstrumentation("sut", supportedModules)(returning(expectedResult)))
      sut.installOn(builder, instrumentation, modules) shouldBe (expectedResult)
  }

  it should "not execute instrumenting when no version match" in test {
    case (instrumentation, builder) =>
      val supportedModules = modules.foldLeft(SupportedModules.empty) {
        case (supported, (module, _)) => supported ++ (module, SupportedVersion.none)
      }
      val expectedResult = LoadingResult("some.class", "other.class")
      val sut            = Agent(AgentInstrumentation("sut", supportedModules)(returning(expectedResult)))
      sut.installOn(builder, instrumentation, modules) shouldBe LoadingResult.empty
  }

  it should "not execute instrumenting when any version doesn't match" in test {
    case (instrumentation, builder) =>
      val supportedModules = modules.foldLeft(SupportedModules.empty) {
        case (supported, (module, version)) => supported ++ (module, SupportedVersion(version))
      } ++ (testModuleOne, SupportedVersion.none)
      val expectedResult = LoadingResult("some.class", "other.class")
      val sut            = Agent(AgentInstrumentation("sut", supportedModules)(returning(expectedResult)))
      sut.installOn(builder, instrumentation, modules) shouldBe LoadingResult.empty
  }

  it should "partially instrument when several agent instrumentations are defined" in test {
    case (instrumentation, builder) =>
      val successfulInstrumentationResult = LoadingResult("some.class")
      val successfulInstrumentation =
        AgentInstrumentation("success", SupportedModules(testModuleOne, SupportedVersion(moduleOneVersion)))(
          returning(successfulInstrumentationResult)
        )
      val failingInstrumentationResult = LoadingResult("other.class")
      val failingInstrumentation =
        AgentInstrumentation("success", SupportedModules(testModuleTwo, SupportedVersion.none))(
          returning(failingInstrumentationResult)
        )
      val sut = Agent(successfulInstrumentation) ++ failingInstrumentation

      sut.installOn(builder, instrumentation, modules) shouldBe successfulInstrumentationResult
  }
}
