package io.scalac.agent.utils

import org.scalatest.{ BeforeAndAfterAll, Suite }

trait DynamicAgentLoaderOps extends BeforeAndAfterAll { this: Suite =>

  def loadAgent(): Unit

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    if (!AgentByArgumentDetector.isPresent) loadAgent()
  }

}
