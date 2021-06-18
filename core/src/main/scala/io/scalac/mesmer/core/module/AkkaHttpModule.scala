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

object AkkaHttpModule extends Module with AkkaHttpMetricsModule {
  val name: String = "akka-http"

  override type All[T] = Metrics[T]

  def enabled(config: Config): All[Boolean] =
    new AkkaHttpMetricsDef[Boolean] {

      val requestTime: Boolean    = true
      val requestCounter: Boolean = true
    }
}
