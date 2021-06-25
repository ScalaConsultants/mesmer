package io.scalac.mesmer.agent

import io.scalac.mesmer.agent.Agent.LoadingResult
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AgentInstrumentationTest extends AnyFlatSpec with Matchers {

  behavior of "AgentInstrumentation"

  private def returning(result: LoadingResult): (Any, Any) => LoadingResult = (_, _) => result

  it should "be equal if tags and name are the same" in {
    val name = "some.class"
    val tags = Set("tag1", "tag2")

    val agent  = AgentInstrumentation(name, tags)(returning(LoadingResult.empty))
    val agent2 = AgentInstrumentation(name, tags)(returning(LoadingResult.empty))

    agent should be(agent2)
  }

  it should "be not be equal if tags are different" in {
    val name  = "some.class"
    val tags1 = Set("tag1", "tag2")
    val tags2 = Set("tag2", "tag3")

    val agent  = AgentInstrumentation(name, tags1)(returning(LoadingResult.empty))
    val agent2 = AgentInstrumentation(name, tags2)(returning(LoadingResult.empty))

    agent should not be (agent2)
  }

  it should "be not be equal if names are different" in {
    val name  = "some.class"
    val name2 = "other.class"
    val tags  = Set("tag1", "tag2")

    val agent  = AgentInstrumentation(name, tags)(returning(LoadingResult.empty))
    val agent2 = AgentInstrumentation(name2, tags)(returning(LoadingResult.empty))

    agent should not be (agent2)
  }
}
