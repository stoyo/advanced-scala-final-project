package fmi.scala

import cats.effect.kernel.Resource
import cats.effect.{IO, IOApp}
import cats.implicits._
import io.circe.config.parser
import fmi.scala.config.ConfigJsonCodecs._
import fmi.scala.config.PictureTaggingAppConfig
import fmi.scala.infrastructure.JwtService
import fmi.scala.infrastructure.db.DbModule
import fmi.scala.picture.{ClarifaiService, ImaggaService, PictureModule, XimilarService}
import fmi.scala.tagging.provider.TaggingProviderModule
import fmi.scala.user.UserModule
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.implicits._
import org.http4s.server.Server

// TODO: add tests
// TODO: start returning some page model with pagination info instead of array of results directly
// TODO: add method not allowed responses where appropriate
// TODO: stop returning non-json responses
// TODO: consider moving dependency on clarifai/ imagga/ ximilar to tagging provider
// TODO: investigate a better way to query db based on tags (maybe some intersection.size > 0)
// TODO: general refactoring and optimizations
object PictureTaggingApp extends IOApp.Simple {
  val app: Resource[IO, Server] = for {
    config <- Resource.eval(parser.decodePathF[IO, PictureTaggingAppConfig]("pictureTaggingApp"))
    computeExecutionContext <- Resource.liftK(IO.executionContext)

    jwtService = new JwtService(config.jwt)

    dbModule <- DbModule(config.database)

    imaggaService = new ImaggaService(config.imagga)
    ximilarService = new XimilarService(config.ximilar)
    clarifaiService = new ClarifaiService(config.clarifai)

    usersModule <- UserModule(dbModule.dbTransactor, jwtService)
    taggingProviderModule <- TaggingProviderModule(dbModule.dbTransactor)
    pictureModule <- PictureModule(
      dbModule.dbTransactor,
      taggingProviderModule.taggingProviderService,
      imaggaService,
      ximilarService,
      clarifaiService)

    nonAuthenticatedRoutes =
    usersModule.routes <+> taggingProviderModule.routes <+> pictureModule.routes
    authenticatedRoutes = usersModule.authMiddleware {
      pictureModule.authenticatedRoutes
    }

    routes = (nonAuthenticatedRoutes <+> authenticatedRoutes).orNotFound

    httpServer <- BlazeServerBuilder[IO](computeExecutionContext)
      .bindHttp(config.http.port, "localhost")
      .withHttpApp(routes)
      .resource
  } yield httpServer

  def run: IO[Unit] =
    app.use(_ => IO.never)
      .onCancel(IO.println("Bye, see you again \uD83D\uDE0A"))
}
