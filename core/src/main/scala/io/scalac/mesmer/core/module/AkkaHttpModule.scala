package io.scalac.mesmer.core.module

import com.typesafe.config.{ Config => TypesafeConfig }

/**
 * Definition of AkkHttp request related metrics
 */
sealed trait AkkaHttpRequestMetricsModule extends MetricModule {
  this: Module =>

  override type Metrics[T] <: AkkaHttpRequestMetricsDef[T]

  trait AkkaHttpRequestMetricsDef[T] {
    def requestTime: T
    def requestCounter: T
  }
}

sealed trait AkkaHttpConnectionMetricsModule extends MetricModule {
  this: Module =>

  override type Metrics[T] <: AkkaHttpConnectionsMetricsDef[T]

  trait AkkaHttpConnectionsMetricsDef[T] {
    def connections: T
  }
}

object AkkaHttpModule extends MesmerModule with AkkaHttpRequestMetricsModule with AkkaHttpConnectionMetricsModule {

  final case class AkkaHttpModuleConfig(requestTime: Boolean, requestCounter: Boolean, connections: Boolean)
      extends AkkaHttpRequestMetricsDef[Boolean]
      with AkkaHttpConnectionsMetricsDef[Boolean]
      with ModuleConfig {
    lazy val enabled: Boolean =
      requestTime || requestCounter || connections // module is considered enabled if any metric is collected
  }

  val name: String = "akka-http"

  override type Metrics[T] = AkkaHttpRequestMetricsDef[T] with AkkaHttpConnectionsMetricsDef[T]

  override type All[T] = Metrics[T]

  val defaultConfig = AkkaHttpModuleConfig(true, true, true)

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
      AkkaHttpModuleConfig(requestTime = requestTime, requestCounter = requestCounter, connections = connections)
    } else AkkaHttpModuleConfig(false, false, false)

  }
}
