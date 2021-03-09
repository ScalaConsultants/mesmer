package io.scalac.extension.util

import io.scalac.extension.metric.Synchronized
import io.scalac.extension.util.probe.BoundTestProbe.{ Dec, Inc, MetricRecorded }
import io.scalac.extension.util.probe._

trait TestProbeSynchronized extends Synchronized {
  override type Instrument[L] = SyncTestProbeWrapper

  override def atomically[A, B](first: SyncTestProbeWrapper, second: SyncTestProbeWrapper): (A, B) => Unit = {
    def submitValue(value: Long, probe: SyncTestProbeWrapper): Unit = probe match {
      case counter: CounterTestProbeWrapper =>
        if (value >= 0L) counter.probe.ref ! Inc(value) else counter.probe.ref ! Dec(-value)
      case recorder: RecorderTestProbeWrapper =>
        recorder.probe.ref ! MetricRecorded(value)
    }
    (a, b) => {
      submitValue(a.asInstanceOf[Long], first)
      submitValue(b.asInstanceOf[Long], second)
    }
  }
}
