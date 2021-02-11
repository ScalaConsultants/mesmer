package io.scalac.extension.upstream

import com.typesafe.config.Config

abstract class MetricNamesCompanion[MetricNames](
  configPath: String,
  configNames: Seq[String],
  defaults: Option[Seq[String]] = None
) {
  defaults.foreach(d => require(d.size == configNames.size, "configNames.size != defaults.size"))

  private val _defaults = defaults.getOrElse(configNames.map(_.replace("-", "_")))

  // TODO How can we improve this? Can we take advantage of apply method from `case class`' companion object?
  protected def argsApply(args: Seq[String]): MetricNames

  lazy val default: MetricNames = argsApply(_defaults)

  def fromConfig(config: Config): MetricNames = {
    import io.scalac.extension.config.ConfigurationUtils._
    config
      .tryValue(configPath)(_.getConfig)
      .map { metricNamesConfig =>
        val args = configNames
          .zip(_defaults)
          .map { case (configName, default) => metricNamesConfig.tryValue(configName)(_.getString).getOrElse(default) }
        argsApply(args)
      }
      .getOrElse(default)
  }

}
