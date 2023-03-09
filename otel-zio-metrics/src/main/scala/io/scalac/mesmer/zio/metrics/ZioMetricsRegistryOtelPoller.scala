package io.scalac.mesmer.zio.metrics

import java.util.Timer
import java.util.TimerTask

import io.scalac.mesmer.zio.metrics.ZIOMetrics._
import zio.MetricsRegistryClient
import zio.metrics.MetricKey
import zio.metrics.MetricKeyType

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.control.NonFatal

class ZioMetricsRegistryOtelPoller {
  import ZioMetricsRegistryOtelPoller._

  private val timer = new Timer()

  private val instruments = mutable.HashMap.empty[MetricKey[MetricKeyType], OtelInstrument]

  private val task = new TimerTask {
    override def run(): Unit =
      try {
        val snapshot = MetricsRegistryClient.snapshot()

        snapshot.filter { metricPair =>
          !instruments.contains(metricPair.metricKey)
        }.foreach { metricPair =>
          val close = metricPair.metricKey.keyType match {
            case _: MetricKeyType.Counter =>
              () => registerCounterAsyncMetric(metricPair.metricKey.asInstanceOf[MetricKey.Counter]).close()
            case _: MetricKeyType.Gauge =>
              () => registerGaugeAsyncMetric(metricPair.metricKey.asInstanceOf[MetricKey.Gauge]).close()
            case _ =>
              // TODO setup sync instruments for histograms
              () => ()
          }
          instruments.put(metricPair.metricKey, OtelInstrument(() => close()))
        }

        instruments.filter { case (metricKey, _) =>
          !snapshot.exists(metricPair => metricPair.metricKey == metricKey)
        }.foreach { case (metricKey, instrument) =>
          instruments.remove(metricKey)
          instrument.close()
        }

      } catch {
        case NonFatal(_) =>
          // TODO how to log the exception?
          schedule()
      }
  }

  def schedule(): Unit =
    timer.schedule(task, 500.millis.toMillis)

  schedule()
}

object ZioMetricsRegistryOtelPoller {
  case class OtelInstrument(close: () => Unit)
}
