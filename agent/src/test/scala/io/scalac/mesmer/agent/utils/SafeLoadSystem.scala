package io.scalac.mesmer.agent.utils

import akka.actor.ExtendedActorSystem
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.util.Timeout
import akka.{ actor => classic }
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Suite

import scala.concurrent.duration._

trait SafeLoadSystem extends BeforeAndAfterAll {
  this: Suite =>

  implicit protected var system: ActorSystem[Nothing] = _
  implicit val timeout: Timeout                       = 2.seconds

  protected def config: Config = ConfigFactory.load("application-test")

  //dsl
  def createTestProbe[M]: TestProbe[M] = TestProbe[M]()

  def classicSystem: ExtendedActorSystem = system.classicSystem.asInstanceOf[ExtendedActorSystem]

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    system = classic.ActorSystem("test-system", config).toTyped // ensure adapter is in use
  }

  override protected def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }
}
