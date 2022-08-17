package example

import zio._
import zio.metrics.jvm.DefaultJvmMetrics

//NOTE: code originally written by Łukasz Gajowy
object SimpleZioExample extends ZIOAppDefault {

  override def run: ZIO[Any, Throwable, Boolean] = {
    ZioProgram
      .findTheMeaningOfLife(3, Int.MinValue, Int.MaxValue)
      .provide(
        Runtime.enableRuntimeMetrics, // NOTE: refactored by following this zio-metrics-connectors example ( https://github.com/zio/zio-metrics-connectors/blob/zio/series2.x/core/jvm/src/test/scala/zio/metrics/connectors/SampleApp.scala#L15-L71 )
        DefaultJvmMetrics.live.unit   // NOTE: DefaultJvmMetrics.live collects the same JVM metrics as the Prometheus Java client's default exporters
      )
  }

}
