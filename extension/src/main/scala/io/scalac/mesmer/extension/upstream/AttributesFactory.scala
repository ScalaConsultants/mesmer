package io.scalac.mesmer.extension.upstream

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes

import scala.annotation.switch

object AttributesFactory {

  def of(required: (String, String)*)(optionals: (String, Option[String])*): Attributes =
    of(required ++ optionals.collect { case (k, Some(v)) => (k, v) })

  /**
   * Microoptimized version that produce OpenTelemetry attributes from sequence of tuples
   * @param attributes
   * @return
   */
  def of(attributes: Seq[(String, String)]): Attributes = {
    val size = attributes.size
    (size: @switch) match {
      case 1 =>
        val Seq((key, value)) = attributes
        Attributes.of(AttributeKey.stringKey(key), value)
      case 2 =>
        val Seq((key1, value1), (key2, value2)) = attributes
        Attributes.of(AttributeKey.stringKey(key1), (value1), AttributeKey.stringKey(key2), value2)
      case 3 =>
        val Seq((key1, value1), (key2, value2), (key3, value3)) = attributes
        Attributes.of(
          AttributeKey.stringKey(key1),
          value1,
          AttributeKey.stringKey(key2),
          value2,
          AttributeKey.stringKey(key3),
          value3
        )
      case 4 =>
        val Seq((key1, value1), (key2, value2), (key3, value3), (key4, value4)) = attributes
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
        val Seq((key1, value1), (key2, value2), (key3, value3), (key4, value4), (key5, value5)) = attributes
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
        attributes
          .foldLeft(Attributes.builder()) { case (builder, (key, value)) =>
            builder.put(key, value)
          }
          .build()
    }

  }

}
