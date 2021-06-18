package io.scalac.mesmer.core.module

import com.typesafe.config.Config

trait Module {
  def name: String
  type All[_]

  def enabled(config: Config): All[Boolean]
}

trait MetricModule {
  this: Module =>
  override type All[T] <: Metrics[T]
  type Metrics[T]
}

trait TracesModule {
  this: Module =>
  override type All[T] <: Traces[T]
  type Traces[T]
}
