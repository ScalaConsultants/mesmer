package io.scalac.mesmer.agent

import java.lang.instrument.Instrumentation

import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.scalac.mesmer.agent.Agent.LoadingResult
import io.scalac.mesmer.core.model.Module
import io.scalac.mesmer.core.model.SupportedModules
import io.scalac.mesmer.core.model.SupportedVersion
import io.scalac.mesmer.core.model.Version
import io.scalac.mesmer.core.util.ModuleInfo.Modules

class AgentTest extends AnyFlatSpec with Matchers {

  val testModuleOne: Module     = Module("test-module-1")
  val testModuleTwo: Module     = Module("test-module-2")
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

  "Agent" should "execute instrumenting function when version match" in test { case (instrumentation, builder) =>
    val supportedModules = modules.foldLeft(SupportedModules.empty) { case (supported, (module, version)) =>
      supported ++ (module, SupportedVersion(version))
    }
    val expectedResult = LoadingResult("some.class", "other.class")
    val sut            = Agent(AgentInstrumentation("sut", supportedModules, Set.empty)(returning(expectedResult)))
    sut.installOn(builder, instrumentation, modules) shouldBe expectedResult
  }

  it should "not execute instrumenting when no version match" in test { case (instrumentation, builder) =>
    val supportedModules = modules.foldLeft(SupportedModules.empty) { case (supported, (module, _)) =>
      supported ++ (module, SupportedVersion.none)
    }
    val expectedResult = LoadingResult("some.class", "other.class")
    val sut            = Agent(AgentInstrumentation("sut", supportedModules,Set.empty)(returning(expectedResult)))
    sut.installOn(builder, instrumentation, modules) shouldBe LoadingResult.empty
  }

  it should "not execute instrumenting when any version doesn't match" in test { case (instrumentation, builder) =>
    val supportedModules = modules.foldLeft(SupportedModules.empty) { case (supported, (module, version)) =>
      supported ++ (module, SupportedVersion(version))
    } ++ (testModuleOne, SupportedVersion.none)
    val expectedResult = LoadingResult("some.class", "other.class")
    val sut            = Agent(AgentInstrumentation("sut", supportedModules, Set.empty)(returning(expectedResult)))
    sut.installOn(builder, instrumentation, modules) shouldBe LoadingResult.empty
  }

  it should "partially instrument when several agent instrumentations are defined" in test {
    case (instrumentation, builder) =>
      val successfulInstrumentationResult = LoadingResult("some.class")
      val successfulInstrumentation =
        AgentInstrumentation("success", SupportedModules(testModuleOne, SupportedVersion(moduleOneVersion)), Set.empty)(
          returning(successfulInstrumentationResult)
        )
      val failingInstrumentationResult = LoadingResult("other.class")
      val failingInstrumentation =
        AgentInstrumentation("success", SupportedModules(testModuleTwo, SupportedVersion.none), Set.empty)(
          returning(failingInstrumentationResult)
        )
      val sut = Agent(successfulInstrumentation) ++ failingInstrumentation

      sut.installOn(builder, instrumentation, modules) shouldBe successfulInstrumentationResult
  }
}
