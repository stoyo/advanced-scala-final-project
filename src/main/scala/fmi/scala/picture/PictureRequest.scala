package fmi.scala.picture

import cats.syntax.either._

case class PictureRequest(base64EncodedContent: String)

case class PictureResponse(id: Int, path: String, tags: Map[String, Seq[Tag]])
case class Tag(value: String)

trait PictureError {
  def error: String
}

case class PictureTagError(error: String) extends PictureError
case class PictureValidationError(error: String) extends PictureError
case class PictureStoreError(error: String) extends PictureError

object PictureRequest {
  def validate(picture: PictureRequest): Either[PictureError, PictureRequest] =

    picture.asRight // TODO: Validate somehow
}
