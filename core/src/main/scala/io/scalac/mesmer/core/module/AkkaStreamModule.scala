package io.scalac.mesmer.core.module
import com.typesafe.config.{ Config => TypesafeConfig }
import io.scalac.mesmer.core.model.Version
import io.scalac.mesmer.core.module.Module.{ Combine, Traverse }
import io.scalac.mesmer.core.util.LibraryInfo.LibraryInfo

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

object AkkaStreamModule
    extends MesmerModule
    with AkkaStreamMetrics
    with AkkaStreamOperatorMetrics
    with RegisterGlobalConfiguration {

  val name: String = "akka-stream"

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

  protected def extractFromConfig(config: TypesafeConfig): Config = {

    val moduleEnabled = config
      .tryValue("enabled")(_.getBoolean)
      .getOrElse(true)

    if (moduleEnabled) {
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

      Impl[Boolean](
        runningStreamsTotal = runningStreams,
        streamActorsTotal = streamActors,
        streamProcessedMessages = streamProcessed,
        processedMessages = operatorProcessed,
        operators = runningOperators,
        demand = demand
      )
    } else Impl[Boolean](false, false, false, false, false, false)

  }

  override type AkkaJar[T] = Jars[T]

  final case class Jars[T](akkaStream: T, akkaActor: T, akkaActorTyped: T)

  def jarsFromLibraryInfo(info: LibraryInfo): Option[AkkaJar[Version]] =
    for {
      stream     <- info.get(requiredAkkaJars.akkaStream)
      actor      <- info.get(requiredAkkaJars.akkaActor)
      actorTyped <- info.get(requiredAkkaJars.akkaActorTyped)
    } yield Jars(stream, actor, actorTyped)

  val requiredAkkaJars: AkkaJar[String] = Jars("akka-stream", "akka-actor", "akka-actor-typed")

  implicit val combine: Combine[All[Boolean]] = (first, second) => {
    Impl(
      runningStreamsTotal = first.runningStreamsTotal && second.runningStreamsTotal,
      streamActorsTotal = first.streamActorsTotal && second.streamActorsTotal,
      streamProcessedMessages = first.streamProcessedMessages && second.streamProcessedMessages,
      processedMessages = first.processedMessages && second.processedMessages,
      operators = first.operators && second.operators,
      demand = first.demand && second.demand
    )
  }

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
