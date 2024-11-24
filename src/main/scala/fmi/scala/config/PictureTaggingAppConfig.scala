package fmi.scala.config

import fmi.scala.infrastructure.db.DbConfig
import io.circe.Codec

case class PictureTaggingAppConfig(
  jwt: JwtConfig,
  http: HttpConfig,
  database: DbConfig,
  imagga: ImaggaConfig,
  ximilar: XimilarConfig,
  clarifai: ClarifaiConfig
)

object ConfigJsonCodecs {
  import io.circe.generic.semiauto._

  implicit val jwtConfigCodec: Codec[JwtConfig] = deriveCodec
  implicit val httpConfigCodec: Codec[HttpConfig] = deriveCodec
  implicit val dbConfigCodec: Codec[DbConfig] = deriveCodec
  implicit val imaggaCodec: Codec[ImaggaConfig] = deriveCodec
  implicit val ximilarCodec: Codec[XimilarConfig] = deriveCodec
  implicit val clarifaiCodec: Codec[ClarifaiConfig] = deriveCodec
  implicit val pictureTaggingAppConfigCodec: Codec[PictureTaggingAppConfig] = deriveCodec
}
