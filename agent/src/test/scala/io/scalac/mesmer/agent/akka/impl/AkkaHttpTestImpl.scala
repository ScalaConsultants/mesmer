package io.scalac.mesmer.agent.akka.impl

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.{ Deregister, Register }
import akka.actor.typed.scaladsl.adapter._
import akka.{ actor => classic }
import com.typesafe.config.{ Config, ConfigFactory }
import io.scalac.mesmer.core.event.HttpEvent
import io.scalac.mesmer.core.httpServiceKey

import java.util.UUID

object AkkaHttpTestImpl {

  private val testConfig: Config = ConfigFactory.load("application-test")

  def systemWithHttpService(body: ActorSystem[Nothing] => TestProbe[HttpEvent] => Any): Any = {
    val cl = Thread.currentThread().getContextClassLoader
    println(s"Initializing ActorSystem with classLoader ${cl}")
    implicit val typedSystem: ActorSystem[Nothing] =
      classic.ActorSystem(UUID.randomUUID().toString, testConfig, cl).toTyped
    val monitor = TestProbe[HttpEvent]("http-test-probe")

    Receptionist(typedSystem).ref ! Register(httpServiceKey, monitor.ref)

    body(typedSystem)(monitor)

    Receptionist(typedSystem).ref ! Deregister(httpServiceKey, monitor.ref)
  }

}
