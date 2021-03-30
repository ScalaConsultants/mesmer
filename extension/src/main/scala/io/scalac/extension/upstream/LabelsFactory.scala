package io.scalac.extension.upstream

import scala.annotation.switch
import scala.collection.mutable.ArrayBuffer

import io.opentelemetry.api.common.Labels

object LabelsFactory {

  def of(required: (String, String)*)(optionals: (String, Option[String])*): Labels =
    Labels.of(
      (required ++ optionals.collect { case (k, Some(v)) => (k, v) }).flatMap { case (k, v) => Seq(k, v) }: _*
    )

  /**
   * Microoptimized version that produce OpenTelemetry labels from sequence of tuples
   * @param labels
   * @return
   */
  def of(labels: Seq[(String, String)]): Labels = {
    val size = labels.size
    (size: @switch) match {
      case 1 =>
        val Seq((key, value)) = labels
        Labels.of(key, value)
      case 2 =>
        val Seq((key1, value1), (key2, value2)) = labels
        Labels.of(key1, value1, key2, value2)
      case 3 =>
        val Seq((key1, value1), (key2, value2), (key3, value3)) = labels
        Labels.of(key1, value1, key2, value2, key3, value3)
      case 4 =>
        val Seq((key1, value1), (key2, value2), (key3, value3), (key4, value4)) = labels
        Labels.of(key1, value1, key2, value2, key3, value3, key4, value4)
      case 5 =>
        val Seq((key1, value1), (key2, value2), (key3, value3), (key4, value4), (key5, value5)) = labels
        Labels.of(key1, value1, key2, value2, key3, value3, key4, value4, key5, value5)
      case _ =>
        val buffer = new ArrayBuffer[String](size * 2)
        labels.foreach { case (key, value) =>
          buffer.append(key)
          buffer.append(value)
        }

        Labels.of(buffer.toArray: _*)
    }

  }

}
