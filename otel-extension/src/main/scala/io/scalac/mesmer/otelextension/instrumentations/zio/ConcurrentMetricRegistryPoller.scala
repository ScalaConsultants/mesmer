package io.scalac.mesmer.otelextension.instrumentations.zio

import java.util.Timer
import java.util.TimerTask

import zio.metrics.MetricKey
import zio.metrics.MetricKeyType

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.control.NonFatal

import io.scalac.mesmer.otelextension.instrumentations.zio.ZIOMetrics._

class ConcurrentMetricRegistryPoller {

  private val timer = new Timer()

  private val instruments = mutable.HashMap.empty[MetricKey[MetricKeyType], AutoCloseable]

  private val pollingInterval =
    sys.props.get("io.scalac.mesmer.zio.metrics.polling-interval").map(Duration(_)).getOrElse(10.millis).toMillis

  private def task = new TimerTask {
    override def run(): Unit =
      try {
        val snapshot = ConcurrentMetricRegistryClient.snapshot()

        snapshot.filter { metricPair =>
          !instruments.contains(metricPair.metricKey)
        }.foreach { metricPair =>
          val autocloseable = metricPair.metricKey.keyType match {
            case _: MetricKeyType.Counter =>
              registerCounterAsyncMetric(metricPair.metricKey.asInstanceOf[MetricKey.Counter])
            case _: MetricKeyType.Gauge =>
              registerGaugeAsyncMetric(metricPair.metricKey.asInstanceOf[MetricKey.Gauge])
            case _ =>
              // TODO setup sync instruments for histograms
              new AutoCloseable {
                override def close(): Unit = ()
              }
          }
          instruments.put(metricPair.metricKey, autocloseable)
        }

        instruments.filter { case (metricKey, _) =>
          !snapshot.exists(metricPair => metricPair.metricKey == metricKey)
        }.foreach { case (metricKey, autoCloseable) =>
          instruments.remove(metricKey)
          autoCloseable.close()
        }
      } catch {
        case NonFatal(_) =>
        // TODO log the exception
      } finally
        schedule()
  }

  private def schedule(): Unit =
    timer.schedule(task, pollingInterval)

  schedule()
}
