package io.scalac.extension

import akka.actor.PoisonPill
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, ActorSystem, SupervisorStrategy }
import io.scalac.extension.ActorEventsMonitorActor.{ ActorMetricsReader, ReflectiveActorTreeTraverser }
import io.scalac.extension.actor.MutableActorMetricsStorage
import io.scalac.extension.util.TestCase.MonitorTestCaseContext.BasicContext
import io.scalac.extension.util.TestCase.{ MonitorWithBasicContextTestCaseFactory, ProvidedActorSystemTestCaseFactory }
import io.scalac.extension.util.probe.ActorMonitorTestProbe
import io.scalac.extension.util.probe.ObserverCollector.ManualCollectorImpl
import io.scalac.extension.util.{ TestConfig, TestOps }
import org.scalatest.concurrent.Eventually
import org.scalatest.flatspec.AnyFlatSpecLike

import scala.concurrent.duration.{ FiniteDuration, _ }
import scala.util.control.NoStackTrace

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

  override protected def createMonitor(implicit system: ActorSystem[_]) = ActorMonitorTestProbe(
    new ManualCollectorImpl()
  )

  "ActorTest" should "unbind monitors on restart" in testCase { implicit context =>
    eventually {
      monitor.unbinds should be(1)
      monitor.binds should be(3) // Sync x1 + Async x2
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
