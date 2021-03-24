package io.scalac.extension.util.probe

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem

import scala.concurrent.duration.FiniteDuration

/**
 * Emulates backend collector behavior: to register updaters to collect it later.
 */
trait ObserverCollector {
  def update(probe: TestProbe[_], cb: () => Unit): Unit
  def remove(probe: TestProbe[_]): Unit
  def finish(probe: TestProbe[_]): Unit = {
    remove(probe)
    probe.stop()
  }
  def collectAll(): Unit
}

object ObserverCollector {

  trait MapBasedObserverCollector { self: ObserverCollector =>
    private val map                                       = collection.mutable.HashMap.empty[TestProbe[_], () => Unit]
    def update(probe: TestProbe[_], cb: () => Unit): Unit = map(probe) = cb
    def remove(probe: TestProbe[_]): Unit                 = map - probe
    def collectAll(): Unit                                = map.foreach(_._2.apply())
  }

  trait ScheduledCollector { self: ObserverCollector =>
    def pingOffset: FiniteDuration
    def system: ActorSystem[_]
    def start(): Unit =
      system.scheduler.scheduleWithFixedDelay(pingOffset / 2, pingOffset)(() => collectAll())(
        system.executionContext
      )
  }

  trait AutoStartCollector { self: ScheduledCollector =>
    start()
  }

  class CommonCollectorImpl(val pingOffset: FiniteDuration)(implicit val system: ActorSystem[_])
      extends ObserverCollector
      with MapBasedObserverCollector
      with ScheduledCollector
      with AutoStartCollector

}
