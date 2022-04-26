package io.scalac.mesmer.agent

import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.scalac.mesmer.agent.util.i13n.InstrumentationDetails.fqcn
import io.scalac.mesmer.agent.util.i13n.TypeInstrumentation.instrument
import io.scalac.mesmer.agent.utils.AgentInstaller
import io.scalac.mesmer.agent.utils.AgentInstaller.LoadingResult

class AgentTest extends AnyFlatSpec with Matchers {

  behavior of "Agent"

  it should "keep one copy of equal instrumentation" in {

    val instrumentation = instrument(fqcn("name", Set("tag")))

    val agentInstrumentationOne = AgentInstrumentation(instrumentation)
    val agentInstrumentationTwo = AgentInstrumentation(instrumentation)

    val agent = Agent(agentInstrumentationOne, agentInstrumentationTwo)

    agent.instrumentations should have size (1)
  }

  it should "combine result from different agent instrumentations" in {

    val one   = "test_name_one"
    val two   = "test_name_two"
    val three = "test_name_three"

    val agentInstrumentationOne   = AgentInstrumentation(instrument(fqcn(one, Set("tag"))))
    val agentInstrumentationTwo   = AgentInstrumentation(instrument(fqcn(two, Set("tag"))))
    val agentInstrumentationThree = AgentInstrumentation(instrument(fqcn(three, Set("tag"))))

    val expectedResult = LoadingResult(Seq(one, two, three))

    val agent = Agent(agentInstrumentationOne, agentInstrumentationTwo, agentInstrumentationThree)

    AgentInstaller.make(new AgentBuilder.Default(), ByteBuddyAgent.install()).install(agent) should be(expectedResult)
  }

}
