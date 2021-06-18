package io.scalac.mesmer.core.module

import com.typesafe.config.Config
import io.scalac.mesmer.core.config.MesmerConfigurationBase

trait Module {
  def name: String
  type All[_]

  def enabled(config: Config): All[Boolean]
}

trait MesmerModule extends Module with MesmerConfigurationBase {
  override type Result = All[Boolean]

  final def enabled(config: Config): All[Boolean] = fromConfig(config)

  val mesmerConfig = s"module.$name"
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
