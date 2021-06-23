package io.scalac.mesmer.core.module

import com.typesafe.config.{ Config => TypesafeConfig }
import io.scalac.mesmer.core.config.MesmerConfigurationBase

trait Module {
  def name: String
  type All[_]
  type Config = All[Boolean] with ModuleConfig

  def enabled(config: TypesafeConfig): Config
}

trait ModuleConfig {
  def enabled: Boolean
}

trait MesmerModule extends Module with MesmerConfigurationBase {
  override type Result = Config

  final def enabled(config: TypesafeConfig): Config = fromConfig(config)

  val mesmerConfig = s"module.$name"
}

trait MetricsModule {
  this: Module =>
  override type All[T] <: Metrics[T]
  type Metrics[T]
}

trait TracesModule {
  this: Module =>
  override type All[T] <: Traces[T]
  type Traces[T]
}
