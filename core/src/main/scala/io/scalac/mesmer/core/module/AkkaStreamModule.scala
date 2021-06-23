package io.scalac.mesmer.core.module
import com.typesafe.config.{Config => TypesafeConfig}

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

  val name: String = "akka-stream"

  override type Metrics[T] = StreamOperatorMetricsDef[T] with StreamMetricsDef[T]
  override type All[T]     = Metrics[T]

  final case class AkkaStreamModuleConfig(
    runningStreamsTotal: Boolean,
    streamActorsTotal: Boolean,
    streamProcessedMessages: Boolean,
    processedMessages: Boolean,
    operators: Boolean,
    demand: Boolean
  ) extends StreamOperatorMetricsDef[Boolean]
      with StreamMetricsDef[Boolean]
      with ModuleConfig {
    lazy val enabled: Boolean = runningStreamsTotal ||
      streamActorsTotal ||
      streamProcessedMessages ||
      processedMessages ||
      operators ||
      demand
  }

  protected def defaultConfig: Config = AkkaStreamModuleConfig(true, true, true, true, true, true)

  protected def extractFromConfig(config: TypesafeConfig): Config = {

    val runningStreams = config
      .tryValue("running-streams")(_.getBoolean)
      .getOrElse(defaultConfig.runningStreamsTotal)

    val streamActors = config
      .tryValue("stream-actors")(_.getBoolean)
      .getOrElse(defaultConfig.streamActorsTotal)

    val streamProcessed = config
      .tryValue("stream-processed")(_.getBoolean)
      .getOrElse(defaultConfig.streamProcessedMessages)

    val operatorProcessed = config
      .tryValue("operator-processed")(_.getBoolean)
      .getOrElse(defaultConfig.processedMessages)

    val runningOperators = config
      .tryValue("running-operators")(_.getBoolean)
      .getOrElse(defaultConfig.operators)

    val demand = config
      .tryValue("operator-demand")(_.getBoolean)
      .getOrElse(defaultConfig.demand)

    AkkaStreamModuleConfig(
      runningStreamsTotal = runningStreams,
      streamActorsTotal = streamActors,
      streamProcessedMessages = streamProcessed,
      processedMessages = operatorProcessed,
      operators = runningOperators,
      demand = demand
    )
  }

}
