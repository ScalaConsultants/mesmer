package io.scalac.mesmer.core.module
import io.scalac.mesmer.core.model.Version
import io.scalac.mesmer.core.module.Module.CommonJars
import io.scalac.mesmer.core.typeclasses.{Combine, Traverse}
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
    with AkkaStreamOperatorMetrics {

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

  protected def fromMap(properties: Map[String, Boolean]): AkkaStreamModule.Config = {
    val enabled = properties.getOrElse("enabled", true)

    if (enabled) {
      Impl(
        runningStreamsTotal = properties.getOrElse("running.streams", defaultConfig.runningStreamsTotal),
        streamActorsTotal = properties.getOrElse("stream.actors", defaultConfig.streamActorsTotal),
        streamProcessedMessages = properties.getOrElse("stream.processed", defaultConfig.streamProcessedMessages),
        processedMessages = properties.getOrElse("operator.processed", defaultConfig.processedMessages),
        operators = properties.getOrElse("running.operators", defaultConfig.operators),
        demand = properties.getOrElse("operator.demand", defaultConfig.demand)
      )
    } else Impl(false, false, false, false, false, false)

  }

  override type Jars[T] = AkkaStreamsJars[T]

  final case class AkkaStreamsJars[T](akkaActor: T, akkaActorTyped: T, akkaStream: T) extends CommonJars[T]

  def jarsFromLibraryInfo(info: LibraryInfo): Option[Jars[Version]] =
    for {
      actor      <- info.get(JarNames.akkaActor)
      actorTyped <- info.get(JarNames.akkaActorTyped)
      stream     <- info.get(JarNames.akkaStream)
    } yield AkkaStreamsJars(actor, actorTyped, stream)

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
