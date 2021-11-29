package io.scalac.mesmer.extension.upstream

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes

import scala.annotation.switch

object LabelsFactory {

  def of(required: (String, String)*)(optionals: (String, Option[String])*): Attributes =
    of(required ++ optionals.collect { case (k, Some(v)) => (k, v) })

  /**
   * Microoptimized version that produce OpenTelemetry labels from sequence of tuples
   * @param labels
   * @return
   */
  def of(labels: Seq[(String, String)]): Attributes = {
    val size = labels.size
    (size: @switch) match {
      case 1 =>
        val Seq((key, value)) = labels
        Attributes.of(AttributeKey.stringKey(key), value)
      case 2 =>
        val Seq((key1, value1), (key2, value2)) = labels
        Attributes.of(AttributeKey.stringKey(key1), (value1), AttributeKey.stringKey(key2), value2)
      case 3 =>
        val Seq((key1, value1), (key2, value2), (key3, value3)) = labels
        Attributes.of(
          AttributeKey.stringKey(key1),
          value1,
          AttributeKey.stringKey(key2),
          value2,
          AttributeKey.stringKey(key3),
          value3
        )
      case 4 =>
        val Seq((key1, value1), (key2, value2), (key3, value3), (key4, value4)) = labels
        Attributes.of(
          AttributeKey.stringKey(key1),
          value1,
          AttributeKey.stringKey(key2),
          value2,
          AttributeKey.stringKey(key3),
          value3,
          AttributeKey.stringKey(key4),
          value4
        )
      case 5 =>
        val Seq((key1, value1), (key2, value2), (key3, value3), (key4, value4), (key5, value5)) = labels
        Attributes.of(
          AttributeKey.stringKey(key1),
          value1,
          AttributeKey.stringKey(key2),
          value2,
          AttributeKey.stringKey(key3),
          value3,
          AttributeKey.stringKey(key4),
          value4,
          AttributeKey.stringKey(key5),
          value5
        )
      case _ =>
        labels
          .foldLeft(Attributes.builder()) { case (builder, (key, value)) =>
            builder.put(key, value)
          }
          .build()
    }

  }

}
