package io.scalac.mesmer.core.module

import com.typesafe.config.{ Config => TypesafeConfig }

import io.scalac.mesmer.core.model.Version
import io.scalac.mesmer.core.typeclasses.Combine
import io.scalac.mesmer.core.typeclasses.Traverse
import io.scalac.mesmer.core.util.LibraryInfo.LibraryInfo

trait AkkaDispatcherConfigMetricsModule extends MetricsModule {
  this: Module =>

  override type Metrics[T] <: AkkaDispatcherConfigMetricsDef[T]

  trait AkkaDispatcherConfigMetricsDef[T] {
    def minThreads: T
    def maxThreads: T
    def parallelismFactor: T
  }

}

object AkkaDispatcherModule
    extends MesmerModule
    with RegistersGlobalConfiguration
    with AkkaDispatcherConfigMetricsModule {

  final case class Impl[T](minThreads: T, maxThreads: T, parallelismFactor: T) extends AkkaDispatcherConfigMetricsDef[T]

  final case class AkkaDispatcherJars[T](akkaActor: T, akkaActorTyped: T) extends Module.CommonJars[T]

  override type Metrics[T] = AkkaDispatcherConfigMetricsDef[T]
  override type All[T]     = Metrics[T]
  override type Jars[T]    = AkkaDispatcherJars[T]

  override def defaultConfig: Config = Impl[Boolean](true, true, true)

  override protected def extractFromConfig(config: TypesafeConfig): Config = {
    val moduleEnabled = config
      .tryValue("enabled")(_.getBoolean)
      .getOrElse(true)

    if (moduleEnabled) {

      val minThreads = config
        .tryValue("parallelism-min")(_.getBoolean)
        .getOrElse(defaultConfig.minThreads)

      val maxThreads = config
        .tryValue("parallelism-max")(_.getBoolean)
        .getOrElse(defaultConfig.maxThreads)

      val parallelismFactor = config
        .tryValue("parallelism-factor")(_.getBoolean)
        .getOrElse(defaultConfig.parallelismFactor)

      Impl[Boolean](minThreads = minThreads, maxThreads = maxThreads, parallelismFactor = parallelismFactor)
    } else Impl(false, false, false)
  }

  val name: String = "akka-dispatcher"

  override def jarsFromLibraryInfo(info: LibraryInfo): Option[Jars[Version]] =
    for {
      actor      <- info.get(JarNames.akkaActor)
      actorTyped <- info.get(JarNames.akkaActorTyped)
    } yield AkkaDispatcherJars(actor, actorTyped)

  implicit val combineConfig: Combine[All[Boolean]] = (first, second) =>
    Impl(
      minThreads = first.minThreads && second.minThreads,
      maxThreads = first.maxThreads && second.maxThreads,
      parallelismFactor = first.parallelismFactor && second.parallelismFactor
    )

  implicit val traverseAll: Traverse[All] = new Traverse[All] {
    def sequence[T](obj: All[T]): Seq[T] = Seq(
      obj.minThreads,
      obj.maxThreads,
      obj.parallelismFactor
    )
  }
}
