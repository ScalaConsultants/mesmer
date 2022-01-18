package io.scalac.mesmer.core.module

import com.typesafe.config.{ Config => TypesafeConfig }

import io.scalac.mesmer.core.model.Version
import io.scalac.mesmer.core.module.Module.Combine
import io.scalac.mesmer.core.module.Module.CommonJars
import io.scalac.mesmer.core.module.Module.JarsNames
import io.scalac.mesmer.core.module.Module.Traverse
import io.scalac.mesmer.core.util.LibraryInfo.LibraryInfo

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

object AkkaHttpModule
    extends MesmerModule
    with RegisterGlobalConfiguration
    with AkkaHttpRequestMetricsModule
    with AkkaHttpConnectionMetricsModule {

  final case class Impl[T](requestTime: T, requestCounter: T, connections: T)
      extends AkkaHttpRequestMetricsDef[T]
      with AkkaHttpConnectionsMetricsDef[T]

  val name: String = "akka-http"

  override type Metrics[T] = AkkaHttpConnectionsMetricsDef[T] with AkkaHttpRequestMetricsDef[T]

  override type All[T] = Metrics[T]

  val defaultConfig: Config = Impl[Boolean](true, true, true)

  protected def extractFromConfig(config: TypesafeConfig): Config = {

    val moduleEnabled = config
      .tryValue("enabled")(_.getBoolean)
      .getOrElse(true)

    if (moduleEnabled) {
      val requestTime = config
        .tryValue("request-time")(_.getBoolean)
        .getOrElse(defaultConfig.requestTime)

      val requestCounter = config
        .tryValue("request-counter")(_.getBoolean)
        .getOrElse(defaultConfig.requestCounter)

      val connections = config
        .tryValue("connections")(_.getBoolean)
        .getOrElse(defaultConfig.connections)
      Impl[Boolean](requestTime = requestTime, requestCounter = requestCounter, connections = connections)
    } else Impl(false, false, false)

  }

  override type AkkaJar[T] = Jars[T]

  final case class Jars[T](akkaActor: T, akkaActorTyped: T, akkaHttp: T) extends CommonJars[T]

  def jarsFromLibraryInfo(info: LibraryInfo): Option[AkkaJar[Version]] =
    for {
      actor      <- info.get(JarsNames.akkaActor)
      actorTyped <- info.get(JarsNames.akkaActorTyped)
      http       <- info.get(JarsNames.akkaHttp)
    } yield Jars(actor, actorTyped, http)

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
