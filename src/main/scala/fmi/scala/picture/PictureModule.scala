package fmi.scala.picture

import cats.effect.{IO, Resource}
import fmi.scala.infrastructure.db.DoobieDatabase.DbTransactor
import fmi.scala.tagging.provider.TaggingProviderService
import fmi.scala.user.AuthenticatedUser
import org.http4s.{AuthedRoutes, HttpRoutes}

case class PictureModule(
  pictureRepository: PictureRepository,
  pictureService: PictureService,
  routes: HttpRoutes[IO],
  authenticatedRoutes: AuthedRoutes[AuthenticatedUser, IO]
)

object PictureModule {
  def apply(
    dbTransactor: DbTransactor,
    taggingProviderService: TaggingProviderService,
    imaggaService: ImaggaService,
    ximilarService: XimilarService,
    clarifaiService: ClarifaiService
  ): Resource[IO, PictureModule] = {
    val pictureRepository = new PictureRepository(dbTransactor)
    val pictureService = new PictureService(pictureRepository, taggingProviderService,
      imaggaService, ximilarService, clarifaiService)
    val picturesRouter = new PictureRouter(pictureService)

    Resource.pure(PictureModule(
      pictureRepository,
      pictureService,
      picturesRouter.nonAuthenticatedRoutes,
      picturesRouter.authenticatedRoutes
    ))
  }
}
