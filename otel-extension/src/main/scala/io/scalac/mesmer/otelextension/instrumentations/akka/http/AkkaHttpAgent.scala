package io.scalac.mesmer.otelextension.instrumentations.akka.http

import io.opentelemetry.instrumentation.api.config.Config

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.core.module.MesmerModule
import io.scalac.mesmer.core.module.MetricsModule
import io.scalac.mesmer.core.module.Module

case class ConfigFieldValue[T](name: String, value: T)

trait ConfigField[T] {
  def name: String
  def defaultValue: T
  def get(
    config: Config
  ): T // This can read the config field at any time: either at once for all config fields or lazily when needed
}

case class StringConfigField(override val name: String, override val defaultValue: String) extends ConfigField[String] {
  override def get(config: Config): String = ConfigGetters.stringGetter(config, this)
}
case class BooleanConfigField(override val name: String, override val defaultValue: Boolean)
    extends ConfigField[Boolean] {
  override def get(config: Config): Boolean = ConfigGetters.booleanGetter(config, this)
}

object ConfigGetters {
  val booleanGetter: (Config, ConfigField[Boolean]) => Boolean = (config: Config, configField: ConfigField[Boolean]) =>
    config.getBoolean(configField.name, configField.defaultValue)
  val stringGetter: (Config, ConfigField[String]) => String = (config: Config, configField: ConfigField[String]) =>
    config.getString(configField.name, configField.defaultValue)
}

trait HttpMetricsConfig[T] {
  def connections: T
}

trait HttpConnectionsConfigDef extends HttpMetricsConfig[ConfigField[Boolean]] {
  def connections: ConfigField[Boolean] = BooleanConfigField("connections", true)
  def someName: ConfigField[String]     = StringConfigField("someName", "someDefaultValue")
}

trait HttpConnectionsConfigValues extends HttpMetricsConfig[Boolean] {

  def connections: Boolean

  def someAdditionalConfigInformation: String
}

object HttpConnectionsConfigValues {
  def apply(config: Config, configDeclaration: HttpConnectionsConfigDef): HttpConnectionsConfigValues =
    new HttpConnectionsConfigValues {
      override def connections: Boolean                    = configDeclaration.connections.get(config)
      override def someAdditionalConfigInformation: String = configDeclaration.someName.get(config)
    }
}

trait SomeRandom {}

trait ConfigurableAgent[C] {
  def apply(config: C): Agent
}

object HttpAgent extends ConfigurableAgent[HttpConnectionsConfigValues] {
  override def apply(config: HttpConnectionsConfigValues): Agent =
    // Build your agent as you please. DO NOT use the HttpMetricsConfig here
    // - config and agents that are created are 2 separate things.

    // For example: Sometimes you use the same logic for 2 different metrics (no need to duplicate)

    ???

}

//
//object HttpAgent extends MetricsAgent[HttpConnectionsConfig] { self =>
//
//  // This requires the already processed config, not the declaration.
//  // So that means config processing has to happen before this is called
//  def apply(config: HttpConnectionsConfig): Agent = {
//
//    if (config.connections) {
//      // TODO: DO something
//    }
//    ???
//  }
//
//}

//object AkkaHttpAgent extends InstrumentModuleFactory(AkkaHttpModule) with AkkaHttpModule.All[Agent] {
//  override def agent: Agent = ???
//
//}

object AkkaHttpModule extends MesmerModule with AkkaHttpMetricsModule {
  override type Metrics[T] = AkkaHttpMetricsDef[T]

  override protected def fromMap(properties: Map[String, Boolean]): AkkaHttpModule.Config = ???

  override def defaultConfig: AkkaHttpModule.Config with Product = ???

  override def name: String = "akkahttp"

  override type All[T] = Metrics[T]
}

sealed trait AkkaHttpMetricsModule extends MetricsModule {
  this: Module =>

  override type Metrics[T] <: AkkaHttpMetricsDef[T]

  trait AkkaHttpMetricsDef[T] {
    def connections: T
  }

}
