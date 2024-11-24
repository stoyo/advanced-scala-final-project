package fmi.scala.picture

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.util.{Base64, UUID}

import cats.data.{EitherT, NonEmptyList}
import cats.effect.IO
import cats.syntax.parallel._
import fmi.scala.tagging.provider.{TaggingProviderEntity, TaggingProviderService}
import fmi.scala.user.AuthenticatedUser

import scala.util.{Failure, Success, Try}

class PictureService(
  pictureRepository: PictureRepository,
  taggingProviderService: TaggingProviderService,
  imaggaService: ImaggaService,
  ximilarService: XimilarService,
  clarifaiService: ClarifaiService
) {

  val MimeTypeToExtension = Map(
    "image/png" -> ".png",
    "image/x-citrix-png" -> ".png",
    "image/x-png" -> ".png",
    "image/jpeg" -> ".jpeg",
    "image/x-citrix-jpeg" -> ".jpeg"
  )

  val ClarifaiTaggerTitle = "clarifai"
  val XimilarTaggerTitle = "ximilar"
  val ImaggaTaggerTitle = "imagga"

  def getAll(user: AuthenticatedUser, top: Int, skip: Int): IO[List[PictureResponse]] =
    pictureRepository
      .getAll(user, top, skip)
      .map(pictureEntitiesToPictureResponses)

  def get(user: AuthenticatedUser, pictureId: Int): IO[Option[PictureResponse]] =
    pictureRepository
      .get(user, pictureId)
      .map(pictureEntityO => pictureEntityO.map(pictureEntityToPictureResponse))

  def getByTags(
    user: AuthenticatedUser,
    filter: NonEmptyList[NonEmptyList[String]],
    top: Int,
    skip: Int
  ): IO[List[PictureResponse]] =
    pictureRepository
      .getByTags(user, filter, top, skip)
      .map(pictureEntitiesToPictureResponses)

  def storeAndTag(
    user: AuthenticatedUser,
    picture: PictureRequest
  ): IO[Either[PictureError, PictureResponse]] =
    (for {
      picture <- EitherT.fromEither[IO](PictureRequest.validate(picture))
      taggingResponses <- EitherT(tag(picture.base64EncodedContent))
      pictureName <- EitherT(IO.pure(generatePictureName(picture.base64EncodedContent)))
      id <- EitherT(pictureRepository.storePicture(pictureName, taggingResponses, user))
      _ <- EitherT(storePictureInFilesystem(picture.base64EncodedContent, pictureName))
    } yield PictureResponse(
      id,
      "/" + PictureRouter.StorageApiPath + "/" + pictureName,
      taggingResponses.map(tuple => (tuple._1.title, tuple._2)).toMap)
    ).value

  def generatePictureName(base64EncodedPicture: String): Either[Nothing, String] =
    Right(UUID.randomUUID.toString + MimeTypeToExtension(base64EncodedPicture
      .substring(base64EncodedPicture.indexOf(":") + 1, base64EncodedPicture.indexOf(";"))))

  def storePictureInFilesystem(
    base64EncodedPicture: String,
    pictureName: String
  ): IO[Either[PictureError, String]] = {
    val picture = Base64.getDecoder.decode(
      base64EncodedPicture
        .substring(base64EncodedPicture.indexOf(",") + 1)
        .getBytes(StandardCharsets.UTF_8))

    IO {
      Try(Files.write(Paths.get(PictureRouter.StorageFileSystemPath, pictureName), picture)) match {
        case Success(_) => Right(pictureName)
        case Failure(exception) => Left(PictureStoreError(exception.getMessage))
      }
    }
  }

  def tag(
    base64EncodedPicture: String
  ): IO[Either[PictureError, Seq[(TaggingProviderEntity, Seq[Tag])]]] =
    (
      tagImagga(base64EncodedPicture),
      tagXimilar(base64EncodedPicture),
      tagClarifai(base64EncodedPicture)
    )
    .parMapN {
      case (Right(imaggaResponse), Right(ximilarResponse), Right(clarifaiResponse)) =>
        Right(Seq(imaggaResponse, ximilarResponse, clarifaiResponse))
      case _ => Left(PictureTagError("Something went wrong"))
    }

  def tagImagga(
    base64EncodedPicture: String
  ): IO[Either[PictureError, (TaggingProviderEntity, Seq[Tag])]] =
    (getImaggaTaggingProvider, imaggaService.tag(base64EncodedPicture))
      .parMapN(buildServiceTagResult(ImaggaTaggerTitle, _, _))

  def getImaggaTaggingProvider: IO[Option[TaggingProviderEntity]] =
    taggingProviderService.getByTitle(ImaggaTaggerTitle)

  def tagXimilar(
    base64EncodedPicture: String
  ): IO[Either[PictureError, (TaggingProviderEntity, Seq[Tag])]] =
    (getXimilarTaggingProvider, ximilarService.tag(base64EncodedPicture))
      .parMapN(buildServiceTagResult(XimilarTaggerTitle, _, _))

  def getXimilarTaggingProvider: IO[Option[TaggingProviderEntity]] =
    taggingProviderService.getByTitle(XimilarTaggerTitle)

  def tagClarifai(
    base64EncodedPicture: String
  ): IO[Either[PictureError, (TaggingProviderEntity, Seq[Tag])]] =
    (getClarifaiTaggingProvider, clarifaiService.tag(base64EncodedPicture))
      .parMapN(buildServiceTagResult(ClarifaiTaggerTitle, _, _))

  def getClarifaiTaggingProvider: IO[Option[TaggingProviderEntity]] =
    taggingProviderService.getByTitle(ClarifaiTaggerTitle)

  private def buildServiceTagResult(
    taggingProviderName: String,
    taggingProviderO: Option[TaggingProviderEntity],
    tagsFromServiceE: Either[PictureError, Seq[String]],
  ) = {
    (taggingProviderO, tagsFromServiceE) match {
      case (Some(taggingProvider), Right(tags)) => Right((taggingProvider, tags.map(Tag)))
      case (_, Right(_)) =>
        Left(PictureTagError(s"Couldn't find $taggingProviderName provider in database"))
      case (Some(_), Left(error)) =>
        Left(PictureTagError(s"Call to $taggingProviderName API failed: " + error.error))
      case _ => Left(PictureTagError("Something went wrong"))
    }
  }

  private def pictureEntitiesToPictureResponses(
    pictureEntities: List[(Int, String, List[String], List[String])]
  ) =
    pictureEntities.map(pictureEntityToPictureResponse)

  private def pictureEntityToPictureResponse(
    pictureEntity: (Int, String, List[String], List[String])
  ) =
    PictureResponse(
      pictureEntity._1,
      "/" + PictureRouter.StorageApiPath + "/" + pictureEntity._2,
      (pictureEntity._3 zip pictureEntity._4)
        .groupBy(_._2)
        .map(tuple => (tuple._1, tuple._2.map(tagAndProvider => Tag(tagAndProvider._1)))))
}
