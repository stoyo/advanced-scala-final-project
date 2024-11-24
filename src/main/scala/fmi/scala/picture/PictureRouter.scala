package fmi.scala.picture

import java.io.File

import cats.data.NonEmptyList
import cats.effect.IO
import fmi.scala.user.AuthenticatedUser
import io.circe.Codec
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveConfiguredCodec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.{AuthedRoutes, HttpRoutes, QueryParamDecoder, StaticFile}
import org.http4s.dsl.io._

class PictureRouter(pictureService: PictureService) {

  import PictureJsonCodecs._
  import org.http4s.circe.CirceEntityCodec._

  implicit val filterQueryParamDecoder: QueryParamDecoder[NonEmptyList[NonEmptyList[String]]] =
    QueryParamDecoder[String]
      .map(filterQueryParam => {
        print("received: " + NonEmptyList.one(NonEmptyList.one(filterQueryParam)))
        NonEmptyList.fromList(filterQueryParam.split("\\*").toList.map(_.trim())) match {
          case Some(andedExpressions) => andedExpressions.map(expression =>
            NonEmptyList.fromList(expression.split(",").toList.map(_.trim())) match {
              case Some(oredWords) =>
                print(oredWords)
                oredWords
              case None =>
                print(NonEmptyList.one(expression))
                NonEmptyList.one(expression)
            })
          case None =>
            print(NonEmptyList.one(NonEmptyList.one(filterQueryParam)))
            NonEmptyList.one(NonEmptyList.one(filterQueryParam))
        }})

  object OptionalFilterQueryParamMatcher
    extends OptionalQueryParamDecoderMatcher[NonEmptyList[NonEmptyList[String]]]("filter")

  object OptionalTopQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("top")
  object OptionalSkipQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("skip")

  def authenticatedRoutes: AuthedRoutes[AuthenticatedUser, IO] = AuthedRoutes.of[AuthenticatedUser, IO] {
    case authReq @ POST -> Root / "pictures" as user =>
      for {
        picture <- authReq.req.as[PictureRequest]
        maybePicture <- pictureService.storeAndTag(user, picture)
        response <- maybePicture.fold(errors => BadRequest(ErrorResponse(Seq(errors.error))), Created(_))
      } yield response
    case GET -> Root / "pictures"
      :? OptionalFilterQueryParamMatcher(maybeFilter)
      :? OptionalTopQueryParamMatcher(maybeTop)
      :? OptionalSkipQueryParamMatcher(maybeSkip)
      as user =>

      val top = maybeTop match {
        case Some(value) =>
          if (value < 1 || value > PictureRouter.MaxPageSize) PictureRouter.DefaultPageSize
          else value
        case _ => PictureRouter.DefaultPageSize
      }

      val skip = maybeSkip match {
        case Some(value) => value
        case _ => 0
      }

      maybeFilter match {
        case Some(filter) => Ok(pictureService.getByTags(user, filter, top, skip))
        case _ => Ok(pictureService.getAll(user, top, skip))
      }

    case GET -> Root / "pictures" / IntVar(pictureId) as user =>
      pictureService.get(user, pictureId).flatMap(_.fold(NotFound())(Ok(_)))
  }

  def nonAuthenticatedRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> PictureRouter.StorageApiPath /: pictureName =>
      StaticFile
        .fromFile(new File(PictureRouter.StorageFileSystemPath + "/" + pictureName), Some(req))
        .getOrElseF(NotFound())
  }
}

object PictureRouter {

  val DefaultPageSize = 20
  val MaxPageSize = 100
  val StorageApiPath = "storage"
  val StorageFileSystemPath = "/Users/sgenchev/Desktop/storage" // TODO: make configurable
}

object PictureJsonCodecs {
  implicit val configuration: Configuration = Configuration.default.withDiscriminator("type")

  implicit val pictureRequestCodec: Codec[PictureRequest] = deriveCodec
  implicit val pictureResponseCodec: Codec[PictureResponse] = deriveCodec
  implicit val tagCodec: Codec[Tag] = deriveCodec

  implicit val authenticatedUserCodec: Codec[AuthenticatedUser] = deriveCodec

  implicit val pictureValidationErrorCodec: Codec[PictureValidationError] = deriveConfiguredCodec
  implicit val pictureStoreErrorCodec: Codec[PictureStoreError] = deriveConfiguredCodec

  implicit val errorResponseCodec: Codec[ErrorResponse] = deriveCodec
}
