package io.scalac.extension.util.probe

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import io.scalac.extension.util.probe.ObserverCollector.ProbeKey

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.FiniteDuration

/**
 * Emulates backend collector behavior: to register updaters to collect it later.
 */
trait ObserverCollector {
  private[util] def update(probe: TestProbe[_], cb: () => Unit): Unit
  private[util] def finish(probe: TestProbe[_]): Unit = {
    remove(ProbeKey(probe))
    probe.stop()
  }
  protected def remove(probe: ProbeKey): Unit
  def collectAll(): Unit
}

object ObserverCollector {

  /**
   * TestProbeImpl does not implement hashCode so we need to wrap it
   * @param probe underlying probe
   */
  case class ProbeKey(probe: TestProbe[_])

  trait MapBasedObserverCollector extends ObserverCollector {

    private[this] val observers = TrieMap.empty[ProbeKey, () => Unit]

    private[util] def update(probe: TestProbe[_], cb: () => Unit): Unit = observers(ProbeKey(probe)) = cb
    protected def remove(key: ProbeKey): Unit                           = observers - key
    def collectAll(): Unit                                              = observers.foreach(_._2.apply())
  }

  abstract class ScheduledCollector(val pingOffset: FiniteDuration) { self: ObserverCollector =>
    def system: ActorSystem[_]
    def start(): Unit =
      system.scheduler.scheduleWithFixedDelay(pingOffset / 2, pingOffset)(() => collectAll())(
        system.executionContext
      )
  }

  trait AutoStartCollector { self: ScheduledCollector =>
    start()
  }

  class ScheduledCollectorImpl(pingOffset: FiniteDuration)(implicit val system: ActorSystem[_])
      extends ScheduledCollector(pingOffset)
      with MapBasedObserverCollector
      with AutoStartCollector

  class ManualCollectorImpl extends ObserverCollector with MapBasedObserverCollector

}
