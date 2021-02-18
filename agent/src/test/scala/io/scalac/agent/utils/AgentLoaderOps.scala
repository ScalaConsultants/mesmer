package io.scalac.agent.utils

import org.scalatest.{ BeforeAndAfterAll, Suite }

trait AgentLoaderOps extends BeforeAndAfterAll { this: Suite =>

  def loadAgent(): Unit

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    AgentDetector.ifAgentIsNotPresent(loadAgent())
  }

}
