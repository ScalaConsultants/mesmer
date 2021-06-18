package io.scalac.mesmer.core.module

import com.typesafe.config.Config

/**
 * Definition of AkkHttp metric related features
 */
sealed trait AkkaHttpMetricsModule extends MetricModule {
  this: Module =>

  override type Metrics[T] = AkkaHttpMetricsDef[T]

  protected trait AkkaHttpMetricsDef[T] {
    def requestTime: T
    def requestCounter: T
  }
}

object AkkaHttpModule extends MesmerModule with AkkaHttpMetricsModule {

  final case class AkkaHttpModuleConfig(requestTime: Boolean, requestCounter: Boolean) extends All[Boolean]

  val name: String = "akka-http"

  override type All[T] = Metrics[T]

  val defaultConfig = AkkaHttpModuleConfig(true, true)

  protected def extractFromConfig(config: Config): AkkaHttpModule.AkkaHttpMetricsDef[Boolean] = {
    val requestTime = config
      .tryValue("request-time")(_.getBoolean)
      .getOrElse(defaultConfig.requestTime)

    val requestCounter = config
      .tryValue("request-counter")(_.getBoolean)
      .getOrElse(defaultConfig.requestCounter)

    AkkaHttpModuleConfig(requestTime = requestTime, requestCounter = requestCounter)
  }
}
