package fmi.scala.picture

import java.nio.charset.StandardCharsets
import java.util.Base64

import cats.effect.IO
import cats.syntax.either._
import fmi.scala.HttpClient
import fmi.scala.config.ImaggaConfig
import fs2.Stream
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.Uri.{Authority, RegName, Scheme}
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.multipart.Boundary

class ImaggaService(imaggaConfig: ImaggaConfig) {
  import ImaggaJsonCodecs._
  import org.http4s.circe.CirceEntityCodec._

  def tag(base64EncodedPicture: String): IO[Either[PictureError, Seq[String]]] = {
    val boundary = Boundary.create.value

    HttpClient.request[ImaggaTaggingResponse](
      Request(
        method = Method.POST,
        uri = Uri(
          scheme = Some(Scheme.https),
          authority = Some(Authority(host = RegName(imaggaConfig.baseUri))),
          path = Path.unsafeFromString("/v2/tags")),
        headers = Headers(
          ("Content-Type", s"multipart/form-data; boundary=$boundary"),
          ("Authorization", s"Basic $base64EncodeKeyAndSecret")),
        body=Stream[IO, Byte](
          buildMultipartFormDataBody(boundary, base64EncodedPicture)
            .getBytes(StandardCharsets.UTF_8) : _*)))
    .map(response => response.result.tags
      .filter(t => t.confidence > imaggaConfig.confidenceThreshold)
      .map(t => t.tag.en)
      .asRight)
  }

  private def base64EncodeKeyAndSecret: String = Base64.getEncoder.encodeToString(
    (imaggaConfig.apiKey + ":" + imaggaConfig.apiSecret).getBytes(StandardCharsets.UTF_8))

  private def buildMultipartFormDataBody(boundary: String, base64EncodedPicture: String) =
    s"""--$boundary
      |Content-Disposition: form-data; name="image_base64"
      |
      |$base64EncodedPicture
      |--$boundary--
      |""".stripMargin
}

object ImaggaJsonCodecs {

  case class ImaggaTaggingResponse(status: Status, result: Result)
  case class Status(`type`: String, text: String)
  case class Result(tags: Seq[TagResult])
  case class TagResult(confidence: Double, tag: TagValue)
  case class TagValue(en: String)

  implicit val imaggaTaggingResponseCodec: Codec[ImaggaTaggingResponse] = deriveCodec
  implicit val statusCodec: Codec[Status] = deriveCodec
  implicit val resultCodec: Codec[Result] = deriveCodec
  implicit val tagResultCodec: Codec[TagResult] = deriveCodec
  implicit val tagValueCodec: Codec[TagValue] = deriveCodec
}
