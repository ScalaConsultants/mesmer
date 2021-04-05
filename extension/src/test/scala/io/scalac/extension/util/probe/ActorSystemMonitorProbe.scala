package io.scalac.extension.util.probe

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import io.scalac.extension.metric.ActorSystemMonitor
import io.scalac.extension.metric.ActorSystemMonitor.BoundMonitor
import io.scalac.extension.util.probe.BoundTestProbe.CounterCommand

final case class ActorSystemMonitorProbe(
  globalProbe: TestProbe[CounterCommand],
  createdActorsProbe: TestProbe[CounterCommand],
  terminatedActorsProbe: TestProbe[CounterCommand]
) extends ActorSystemMonitor
    with BindUnbindMonitor {
  override def bind(labels: ActorSystemMonitor.Labels): ActorSystemMonitor.BoundMonitor = {
    onBind()
    new ActorSystemTestBoundMonitor with UnbindMonitor
  }

  class ActorSystemTestBoundMonitor extends BoundMonitor {

    override lazy val createdActors = UpDownCounterTestProbeWrapper(createdActorsProbe, Some(globalProbe))

    override lazy val terminatedActors = UpDownCounterTestProbeWrapper(terminatedActorsProbe, Some(globalProbe))

    override private[scalac] def unbind(): Unit = ()
  }
}

object ActorSystemMonitorProbe {
  def apply(implicit system: ActorSystem[_]): ActorSystemMonitorProbe = ActorSystemMonitorProbe(
    TestProbe(),
    TestProbe(),
    TestProbe()
  )

}
