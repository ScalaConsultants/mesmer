package io.scalac.domain

import io.circe.generic.semiauto._
import io.circe.{ Decoder, Encoder }
import io.circe.Codec
import io.circe.Json

trait JsonCodecs {
  implicit val accountCodec: Codec[Account]                  = deriveCodec
  implicit val aplicationErrorCodec: Codec[ApplicationError] = deriveCodec

  protected implicit class EncoderOps[T: Encoder](value: T) {
    def asField(field: String): Json =
      Json.obj(field -> Encoder[T].apply(value))
  }

}
