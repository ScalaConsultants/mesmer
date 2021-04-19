package io.scalac.extension

import akka.actor.PoisonPill
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.scaladsl.Behaviors
import org.scalatest.concurrent.Eventually
import org.scalatest.flatspec.AnyFlatSpecLike

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import scala.util.control.NoStackTrace

import io.scalac.core.actor.MutableActorMetricsStorage
import io.scalac.core.util.TestCase.MonitorTestCaseContext.BasicContext
import io.scalac.core.util.TestCase.MonitorWithBasicContextTestCaseFactory
import io.scalac.core.util.TestCase.ProvidedActorSystemTestCaseFactory
import io.scalac.core.util.TestConfig
import io.scalac.core.util.TestOps
import io.scalac.core.util.probe.ActorMonitorTestProbe
import io.scalac.core.util.probe.ObserverCollector.ManualCollectorImpl
import io.scalac.extension.ActorEventsMonitorActor.ActorMetricsReader
import io.scalac.extension.ActorEventsMonitorActor.ReflectiveActorTreeTraverser

class ActorEventMonitorActorRestartTest
    extends ScalaTestWithActorTestKit(TestConfig.localActorProvider)
    with MonitorWithBasicContextTestCaseFactory
    with ProvidedActorSystemTestCaseFactory
    with AnyFlatSpecLike
    with Eventually
    with TestOps {

  type Setup   = ActorRef[_]
  type Monitor = ActorMonitorTestProbe

  protected val pingOffset: FiniteDuration = scaled(1.seconds)

  protected val reasonableTime: FiniteDuration = 3 * pingOffset

  override implicit val patience: PatienceConfig = PatienceConfig(reasonableTime, scaled(150.millis))

  val FailingReader: ActorMetricsReader = _ => throw new RuntimeException("Planned failure") with NoStackTrace

  protected def createMonitor(implicit system: ActorSystem[_]): Monitor =
    ActorMonitorTestProbe(new ManualCollectorImpl())

  "ActorTest" should "unbind monitors on restart" in testCase { implicit context =>
    eventually {
      monitor.unbinds should be(1)
      monitor.binds should be(2)
    }
  }

  def setUp(context: BasicContext[ActorMonitorTestProbe]): Setup =
    context.system.systemActorOf(
      Behaviors
        .supervise(
          ActorEventsMonitorActor(
            monitor(context),
            None,
            pingOffset,
            MutableActorMetricsStorage.empty,
            system.systemActorOf(Behaviors.ignore[AkkaStreamMonitoring.Command], createUniqueId),
            actorMetricsReader = FailingReader,
            actorTreeTraverser = ReflectiveActorTreeTraverser
          )
        )
        .onFailure(SupervisorStrategy.restart),
      "test"
    )

  def tearDown(setup: Setup): Unit = setup.unsafeUpcast[Any] ! PoisonPill
}
