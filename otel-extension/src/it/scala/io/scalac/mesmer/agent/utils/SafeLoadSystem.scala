package io.scalac.mesmer.agent.utils

import akka.actor.ExtendedActorSystem
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.util.Timeout
import akka.{ actor => classic }
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.{ BeforeAndAfterAll, Suite }

import scala.concurrent.duration._

trait SafeLoadSystem extends BeforeAndAfterAll {
  this: Suite =>

  implicit protected var system: ActorSystem[Nothing] = _
  implicit val timeout: Timeout                       = 2.seconds

  // dsl
  def createTestProbe[M]: TestProbe[M] = TestProbe[M]()

  def classicSystem: ExtendedActorSystem = system.classicSystem.asInstanceOf[ExtendedActorSystem]

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    system = classic.ActorSystem("test-system", config).toTyped // ensure adapter is in use
  }

  protected def config: Config = ConfigFactory.load("application-test")

  override protected def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }
}
