package example.domain

import io.circe.Codec
import io.circe.Encoder
import io.circe.Json
import io.circe.generic.semiauto._

trait JsonCodecs {
  implicit val accountCodec: Codec[Account]                   = deriveCodec
  implicit val applicationErrorCodec: Codec[ApplicationError] = deriveCodec

  protected implicit class EncoderOps[T: Encoder](value: T) {
    def asField(field: String): Json =
      Json.obj(field -> Encoder[T].apply(value))
  }

}
