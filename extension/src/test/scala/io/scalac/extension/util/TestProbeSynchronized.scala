package io.scalac.extension.util

import io.scalac.extension.metric.Synchronized
import io.scalac.extension.util.probe.{
  AbstractTestProbeWrapper,
  CounterTestProbeWrapper,
  ObserverTestProbeWrapper,
  RecorderTestProbeWrapper
}
import io.scalac.extension.util.probe.BoundTestProbe.{ Dec, Inc, MetricObserved, MetricRecorded }

trait TestProbeSynchronized extends Synchronized {
  override type Instrument[L] = AbstractTestProbeWrapper

  override def atomically[A, B](first: AbstractTestProbeWrapper, second: AbstractTestProbeWrapper): (A, B) => Unit = {
    def submitValue(value: Long, probe: AbstractTestProbeWrapper): Unit = probe match {
      case counter: CounterTestProbeWrapper =>
        if (value >= 0L) counter.probe.ref ! Inc(value) else counter.probe.ref ! Dec(-value)
      case recorder: RecorderTestProbeWrapper =>
        recorder.probe.ref ! MetricRecorded(value)
      case observer: ObserverTestProbeWrapper =>
        observer.probe.ref ! MetricObserved(value)
    }
    (a, b) => {
      submitValue(a.asInstanceOf[Long], first)
      submitValue(b.asInstanceOf[Long], second)
    }
  }
}
