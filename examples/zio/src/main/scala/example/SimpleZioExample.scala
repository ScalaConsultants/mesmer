package example

import zio._
import zio.metrics.jvm.DefaultJvmMetrics

import scala.io.StdIn

object SimpleZioExample extends ZIOAppDefault {

  override def run: ZIO[Any, Throwable, Any] = ZioProgram
    .findTheMeaningOfLife(3, Int.MinValue, Int.MaxValue)
    .race(ZIO.attempt(StdIn.readLine()))
    .provide(
      Runtime.enableRuntimeMetrics,
      DefaultJvmMetrics.live.unit
    )
}
