package io.scalac.mesmer.core.module
import io.scalac.mesmer.core.typeclasses.Combine
import io.scalac.mesmer.core.typeclasses.Traverse

/**
 * Definition of AkkHttp request related metrics
 */
sealed trait AkkaHttpRequestMetricsModule extends MetricsModule {
  this: Module =>

  override type Metrics[T] <: AkkaHttpRequestMetricsDef[T]

  trait AkkaHttpRequestMetricsDef[T] {
    def requestTime: T
    def requestCounter: T
  }
}

sealed trait AkkaHttpConnectionMetricsModule extends MetricsModule {
  this: Module =>

  override type Metrics[T] <: AkkaHttpConnectionsMetricsDef[T]

  trait AkkaHttpConnectionsMetricsDef[T] {
    def connections: T
  }
}

object AkkaHttpModule extends MesmerModule with AkkaHttpRequestMetricsModule with AkkaHttpConnectionMetricsModule {

  final case class Impl[T](requestTime: T, requestCounter: T, connections: T)
      extends AkkaHttpRequestMetricsDef[T]
      with AkkaHttpConnectionsMetricsDef[T]

  lazy val name: String = "akkahttp"

  override type Metrics[T] = AkkaHttpConnectionsMetricsDef[T] with AkkaHttpRequestMetricsDef[T]

  override type All[T] = Metrics[T]

  val defaultConfig: Config = Impl[Boolean](true, true, true)

  protected def fromMap(properties: Map[String, Boolean]): AkkaHttpModule.Config = {
    val enabled = properties.getOrElse("enabled", true)

    if (enabled) {
      Impl(
        requestTime = properties.getOrElse("request.time", defaultConfig.requestTime),
        requestCounter = properties.getOrElse("request.counter", defaultConfig.requestCounter),
        connections = properties.getOrElse("connections", defaultConfig.connections)
      )
    } else Impl(false, false, false)

  }

  implicit val combineConfig: Combine[All[Boolean]] = (first, second) =>
    Impl(
      requestTime = first.requestTime && second.requestTime,
      requestCounter = first.requestCounter && second.requestCounter,
      connections = first.connections && second.connections
    )

  implicit val traverseAll: Traverse[All] = new Traverse[All] {
    def sequence[T](obj: All[T]): Seq[T] = Seq(
      obj.requestTime,
      obj.requestCounter,
      obj.connections
    )
  }
}
