package io.scalac.extension.metric
import io.opentelemetry.api.common.Labels

trait OpenTelemetryLabels {
  def toOpenTelemetry: Labels
  protected def convert(required: Seq[(String, String)], optional: (String, Option[String])*): Labels = {
    val allLabels: Seq[String] = (optional.collect {
      case (name, Some(value)) => (name, value)
    } ++ required).flatMap {
      case (name, value) => Seq(name, value)
    }

    Labels.of(allLabels.toArray)
  }
}
