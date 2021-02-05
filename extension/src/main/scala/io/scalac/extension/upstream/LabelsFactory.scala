package io.scalac.extension.upstream

import io.opentelemetry.api.common.Labels

object LabelsFactory {

  def of(required: (String, String)*)(optionals: (String, Option[String])*): Labels =
    Labels.of(
      (required ++ optionals.collect { case (k, Some(v)) => (k, v) }).flatMap { case (k, v) => Seq(k, v) }.toArray
    )

}
