package io.scalac.core.util

import io.scalac.core.util.probe.BoundTestProbe.Dec
import io.scalac.core.util.probe.BoundTestProbe.Inc
import io.scalac.core.util.probe.BoundTestProbe.MetricRecorded
import io.scalac.core.util.probe.RecorderTestProbeWrapper
import io.scalac.core.util.probe.SyncTestProbeWrapper
import io.scalac.core.util.probe.UpDownCounterTestProbeWrapper
import io.scalac.extension.metric.Synchronized

trait TestProbeSynchronized extends Synchronized {
  type Instrument[L] = SyncTestProbeWrapper

  def atomically[A, B](first: SyncTestProbeWrapper, second: SyncTestProbeWrapper): (A, B) => Unit = {
    def submitValue(value: Long, probe: SyncTestProbeWrapper): Unit = probe match {
      case counter: UpDownCounterTestProbeWrapper =>
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
