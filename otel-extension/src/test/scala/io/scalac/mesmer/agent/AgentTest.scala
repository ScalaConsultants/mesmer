package io.scalac.mesmer.agent

import java.util

import io.opentelemetry.javaagent.extension.instrumentation.{ TypeInstrumentation => OtelTypeInstrumentation }
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.scalac.mesmer.agent.util.i13n.InstrumentationDetails.fqcn
import io.scalac.mesmer.agent.util.i13n.TypeInstrumentation.instrument

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

    val agentInstrumentationOne: AgentInstrumentation = AgentInstrumentation(instrument(fqcn(one, Set("tag"))))
    val agentInstrumentationTwo                       = AgentInstrumentation(instrument(fqcn(two, Set("tag"))))
    val agentInstrumentationThree                     = AgentInstrumentation(instrument(fqcn(three, Set("tag"))))

    val agent = Agent(agentInstrumentationOne, agentInstrumentationTwo, agentInstrumentationThree)

    val instrumentations: util.List[OtelTypeInstrumentation] = agent.asOtelTypeInstrumentations

    new util.HashSet(instrumentations) should have size 3
  }
}
