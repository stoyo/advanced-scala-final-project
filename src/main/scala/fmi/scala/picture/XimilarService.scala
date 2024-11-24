package fmi.scala.picture

import cats.effect.IO
import cats.syntax.either._
import fmi.scala.HttpClient
import fmi.scala.config.XimilarConfig
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.Uri.{Authority, RegName, Scheme}
import org.http4s._
import org.http4s.dsl.io._

class XimilarService(ximilarConfig: XimilarConfig) {
  import XimilarJsonCodecs._
  import org.http4s.circe.CirceEntityCodec._

  def tag(base64EncodedPicture: String): IO[Either[PictureError, Seq[String]]] =
    HttpClient.request[XimilarTaggingResponse](
      Request(
        method = Method.POST,
        uri = Uri(
          scheme = Some(Scheme.https),
          authority = Some(Authority(host = RegName(ximilarConfig.baseUri))),
          path = Path.unsafeFromString("/tagging/generic/v2/tags")),
        headers = Headers(
          ("Authorization", s"Token ${ximilarConfig.token}")))
    .withEntity(XimilarTaggingRequest(Seq(RecordRequest(base64EncodedPicture)))))
    .map(response => response.records.head._tags
      .filter(t => t.prob > ximilarConfig.confidenceThreshold)
      .map(t => t.name)
      .asRight)
}

object XimilarJsonCodecs {

  case class XimilarTaggingRequest(records: Seq[RecordRequest])
  case class RecordRequest(_base64: String)

  case class XimilarTaggingResponse(status: Status, records: Seq[RecordResponse])
  case class Status(code: Int, text: String)
  case class RecordResponse(_tags: Seq[Tag])
  case class Tag(prob: Double, name: String)

  implicit val ximilarTaggingRequestCodec: Codec[XimilarTaggingRequest] = deriveCodec
  implicit val recordRequestCodec: Codec[RecordRequest] = deriveCodec

  implicit val ximilarTaggingResponseCodec: Codec[XimilarTaggingResponse] = deriveCodec
  implicit val statusCodec: Codec[Status] = deriveCodec
  implicit val recordResponseCodec: Codec[RecordResponse] = deriveCodec
  implicit val tagCodec: Codec[Tag] = deriveCodec
}
