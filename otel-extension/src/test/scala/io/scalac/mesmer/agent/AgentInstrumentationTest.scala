package io.scalac.mesmer.agent

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.scalac.mesmer.agent.util.i13n.InstrumentationDetails._
import io.scalac.mesmer.agent.util.i13n.TypeInstrumentation._

class AgentInstrumentationTest extends AnyFlatSpec with Matchers {

  behavior of "AgentInstrumentation"

  it should "be equal if tags and name are the same" in {

    val typeInstrumentation = instrument(fqcn("some.class", Set("tag1", "tag2")))

    val agent  = AgentInstrumentation(typeInstrumentation)
    val agent2 = AgentInstrumentation(typeInstrumentation)

    agent should be(agent2)
  }

  it should "be not be equal if tags are different" in {
    val name = "some.class"

    val agent  = AgentInstrumentation(instrument(fqcn(name, Set("tag1", "tag2"))))
    val agent2 = AgentInstrumentation(instrument(fqcn(name, Set("tag2", "tag3"))))

    agent should not be agent2
  }

  it should "be not be equal if names are different" in {
    val tags = Set("tag1", "tag2")

    val agent  = AgentInstrumentation(instrument(fqcn("some.class", tags)))
    val agent2 = AgentInstrumentation(instrument(fqcn("other.class", tags)))

    agent should not be agent2
  }
}
