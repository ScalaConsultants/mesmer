package io.scalac.mesmer.core.module

import io.scalac.mesmer.core.typeclasses.{ Combine, Traverse }

sealed trait AkkaStreamMetrics extends MetricsModule {
  this: Module =>

  override type Metrics[T] <: StreamMetricsDef[T]

  trait StreamMetricsDef[T] {
    def runningStreamsTotal: T
    def streamActorsTotal: T
    def streamProcessedMessages: T
  }

}

sealed trait AkkaStreamOperatorMetrics extends MetricsModule {
  this: Module =>

  override type Metrics[T] <: StreamOperatorMetricsDef[T]

  trait StreamOperatorMetricsDef[T] {
    def processedMessages: T
    def operators: T
    def demand: T
  }

}

object AkkaStreamModule extends MesmerModule with AkkaStreamMetrics with AkkaStreamOperatorMetrics {

  lazy val name: String = "akkastream"

  override type Metrics[T] = StreamOperatorMetricsDef[T] with StreamMetricsDef[T]
  override type All[T]     = Metrics[T]

  final case class Impl[T](
    runningStreamsTotal: T,
    streamActorsTotal: T,
    streamProcessedMessages: T,
    processedMessages: T,
    operators: T,
    demand: T
  ) extends StreamOperatorMetricsDef[T]
      with StreamMetricsDef[T]

  val defaultConfig: Config = Impl(true, true, true, true, true, true)

  protected def fromMap(properties: Map[String, Boolean]): AkkaStreamModule.Config =
    Impl(
      runningStreamsTotal = properties.getOrElse("running.streams", defaultConfig.runningStreamsTotal),
      streamActorsTotal = properties.getOrElse("stream.actors", defaultConfig.streamActorsTotal),
      streamProcessedMessages = properties.getOrElse("stream.processed", defaultConfig.streamProcessedMessages),
      processedMessages = properties.getOrElse("operator.processed", defaultConfig.processedMessages),
      operators = properties.getOrElse("running.operators", defaultConfig.operators),
      demand = properties.getOrElse("operator.demand", defaultConfig.demand)
    )

  implicit val combine: Combine[All[Boolean]] = (first, second) =>
    Impl(
      runningStreamsTotal = first.runningStreamsTotal && second.runningStreamsTotal,
      streamActorsTotal = first.streamActorsTotal && second.streamActorsTotal,
      streamProcessedMessages = first.streamProcessedMessages && second.streamProcessedMessages,
      processedMessages = first.processedMessages && second.processedMessages,
      operators = first.operators && second.operators,
      demand = first.demand && second.demand
    )

  implicit val traverseAll: Traverse[All] = new Traverse[All] {
    def sequence[T](obj: All[T]): Seq[T] = Seq(
      obj.runningStreamsTotal,
      obj.streamActorsTotal,
      obj.streamProcessedMessages,
      obj.processedMessages,
      obj.operators,
      obj.demand
    )
  }
}
