package fmi.scala.picture

import cats.effect.IO
import cats.syntax.either._
import fmi.scala.HttpClient
import fmi.scala.config.ClarifaiConfig
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.Uri.{Authority, RegName, Scheme}
import org.http4s._
import org.http4s.dsl.io._

class ClarifaiService(clarifaiConfig: ClarifaiConfig) {
  import ClarifaiJsonCodecs._
  import org.http4s.circe.CirceEntityCodec._

  def tag(base64EncodedPicture: String): IO[Either[PictureError, Seq[String]]] =
    HttpClient.request[ClarifaiTaggingResponse](
      Request(
        method = Method.POST,
        uri = Uri(
          scheme = Some(Scheme.https),
          authority = Some(Authority(host = RegName(clarifaiConfig.baseUri))),
          path = Path.unsafeFromString(s"/v2/models/${clarifaiConfig.modelId}/outputs")),
        headers = Headers(
          ("Authorization", s"Key ${clarifaiConfig.key}")))
    .withEntity(ClarifaiTaggingRequest(Seq(Input(RequestData(Image(
      base64EncodedPicture.substring(base64EncodedPicture.indexOf(",") + 1))))))))
    .map(response => response.outputs.head.data.concepts
      .filter(c => c.value > clarifaiConfig.confidenceThreshold)
      .map(c => c.name)
      .asRight)
}

object ClarifaiJsonCodecs {

  case class ClarifaiTaggingRequest(inputs: Seq[Input])
  case class Input(data: RequestData)
  case class RequestData(image: Image)
  case class Image(base64: String)

  case class ClarifaiTaggingResponse(status: Status, outputs: Seq[Output])
  case class Status(code: Int, description: String)
  case class Output(data: ResponseData)
  case class ResponseData(concepts: Seq[Concept])
  case class Concept(name: String, value: Double)

  implicit val clarifaiTaggingRequestCodec: Codec[ClarifaiTaggingRequest] = deriveCodec
  implicit val inputCodec: Codec[Input] = deriveCodec
  implicit val requestDataCodec: Codec[RequestData] = deriveCodec
  implicit val imageCodec: Codec[Image] = deriveCodec

  implicit val clarifaiTaggingResponseCodec: Codec[ClarifaiTaggingResponse] = deriveCodec
  implicit val statusCodec: Codec[Status] = deriveCodec
  implicit val outputCodec: Codec[Output] = deriveCodec
  implicit val responseDataCodec: Codec[ResponseData] = deriveCodec
  implicit val conceptCodec: Codec[Concept] = deriveCodec
}
