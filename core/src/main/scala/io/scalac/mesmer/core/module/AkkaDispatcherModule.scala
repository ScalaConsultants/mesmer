package io.scalac.mesmer.core.module
import io.scalac.mesmer.core.typeclasses.Combine
import io.scalac.mesmer.core.typeclasses.Traverse

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
    with AkkaDispatcherConfigMetricsModule
    with AkkaDispatcherThreadCountMetricsModule {

  final case class Impl[T](minThreads: T, maxThreads: T, parallelismFactor: T, totalThreads: T, activeThreads: T)
      extends AkkaDispatcherMinMaxThreadsConfigMetricsDef[T]
      with AkkaDispatcherThreadCountMetricsDef[T]

  override type Metrics[T] = AkkaDispatcherMinMaxThreadsConfigMetricsDef[T] with AkkaDispatcherThreadCountMetricsDef[T]
  override type All[T]     = Metrics[T]

  override def defaultConfig: Config = Impl[Boolean](true, true, true, true, true)

  protected def fromMap(properties: Map[String, Boolean]): AkkaDispatcherModule.Config =
    Impl(
      minThreads = properties.getOrElse("min-threads", defaultConfig.minThreads),
      maxThreads = properties.getOrElse("max-threads", defaultConfig.maxThreads),
      parallelismFactor = properties.getOrElse("parallelism-factor", defaultConfig.parallelismFactor),
      totalThreads = properties.getOrElse("total-threads", defaultConfig.totalThreads),
      activeThreads = properties.getOrElse("active-threads", defaultConfig.activeThreads)
    )

  val name: String = "akka-dispatcher"

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
