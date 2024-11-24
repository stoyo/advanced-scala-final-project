package fmi.scala.tagging.provider

import cats.effect.{IO, Resource}
import fmi.scala.infrastructure.db.DoobieDatabase.DbTransactor
import org.http4s.HttpRoutes

case class TaggingProviderModule (
  taggingProviderRepository: TaggingProviderRepository,
  taggingProviderService: TaggingProviderService,
  routes: HttpRoutes[IO]
)

object TaggingProviderModule {
  def apply(dbTransactor: DbTransactor): Resource[IO, TaggingProviderModule] = {
    val taggingProviderRepository = new TaggingProviderRepository(dbTransactor)
    val taggingProviderService = new TaggingProviderService(taggingProviderRepository)
    val taggingProviderRouter = new TaggingProviderRouter(taggingProviderService)

    Resource.pure(TaggingProviderModule(
      taggingProviderRepository,
      taggingProviderService,
      taggingProviderRouter.nonAuthenticatedRoutes,
    ))
  }
}
