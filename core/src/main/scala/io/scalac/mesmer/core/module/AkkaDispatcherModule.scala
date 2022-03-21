package io.scalac.mesmer.core.module

import com.typesafe.config.{ Config => TypesafeConfig }

import io.scalac.mesmer.core.model.Version
import io.scalac.mesmer.core.typeclasses.Combine
import io.scalac.mesmer.core.typeclasses.Traverse
import io.scalac.mesmer.core.util.LibraryInfo.LibraryInfo

trait AkkaDispatcherConfigMetricsModule extends MetricsModule {
  this: Module =>

  override type Metrics[T] <: AkkaDispatcherMinMaxThreadsConfigMetricsDef[T]

  trait AkkaDispatcherMinMaxThreadsConfigMetricsDef[T] {
    def minThreads: T
    def maxThreads: T
    def parallelismFactor: T
  }

}

trait AkkaDispatcherThreadCountMetricsModule extends MetricsModule {
  this: Module =>

  override type Metrics[T] <: AkkaDispatcherThreadCountMetricsDef[T]

  trait AkkaDispatcherThreadCountMetricsDef[T] {
    def totalThreads: T
    def activeThreads: T
  }

}

object AkkaDispatcherModule
    extends MesmerModule
    with RegistersGlobalConfiguration
    with AkkaDispatcherConfigMetricsModule
    with AkkaDispatcherThreadCountMetricsModule {

  final case class Impl[T](minThreads: T, maxThreads: T, parallelismFactor: T, totalThreads: T, activeThreads: T)
      extends AkkaDispatcherMinMaxThreadsConfigMetricsDef[T]
      with AkkaDispatcherThreadCountMetricsDef[T]

  final case class AkkaDispatcherJars[T](akkaActor: T, akkaActorTyped: T) extends Module.CommonJars[T]

  override type Metrics[T] = AkkaDispatcherMinMaxThreadsConfigMetricsDef[T] with AkkaDispatcherThreadCountMetricsDef[T]
  override type All[T]     = Metrics[T]
  override type Jars[T]    = AkkaDispatcherJars[T]

  override def defaultConfig: Config = Impl[Boolean](true, true, true, true, true)

  override protected def extractFromConfig(config: TypesafeConfig): Config = {
    val moduleEnabled = config
      .tryValue("enabled")(_.getBoolean)
      .getOrElse(true)

    if (moduleEnabled) {

      val minThreads = config
        .tryValue("min-threads")(_.getBoolean)
        .getOrElse(defaultConfig.minThreads)

      val maxThreads = config
        .tryValue("max-threads")(_.getBoolean)
        .getOrElse(defaultConfig.maxThreads)

      val parallelismFactor = config
        .tryValue("parallelism-factor")(_.getBoolean)
        .getOrElse(defaultConfig.parallelismFactor)

      val totalThreads = config
        .tryValue("total-threads")(_.getBoolean)
        .getOrElse(defaultConfig.totalThreads)

      val activeThreads = config
        .tryValue("active-threads")(_.getBoolean)
        .getOrElse(defaultConfig.activeThreads)

      Impl[Boolean](
        minThreads = minThreads,
        maxThreads = maxThreads,
        parallelismFactor = parallelismFactor,
        totalThreads = totalThreads,
        activeThreads = activeThreads
      )
    } else Impl(false, false, false, false, false)
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
      parallelismFactor = first.parallelismFactor && second.parallelismFactor,
      totalThreads = first.totalThreads && second.totalThreads,
      activeThreads = first.activeThreads && second.activeThreads
    )

  implicit val traverseAll: Traverse[All] = new Traverse[All] {
    def sequence[T](obj: All[T]): Seq[T] = Seq(
      obj.minThreads,
      obj.maxThreads,
      obj.parallelismFactor,
      obj.totalThreads,
      obj.activeThreads
    )
  }
}
