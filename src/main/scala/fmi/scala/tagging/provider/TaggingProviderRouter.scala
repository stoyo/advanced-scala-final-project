package fmi.scala.tagging.provider

import cats.effect.IO
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.HttpRoutes
import org.http4s.dsl.io._

class TaggingProviderRouter(taggingProviderService: TaggingProviderService) {
  import TaggingProviderJsonCodecs._
  import org.http4s.circe.CirceEntityCodec._

  def nonAuthenticatedRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "tagging-providers" => Ok(taggingProviderService.getAll)
    case GET -> Root / "tagging-providers" / IntVar(taggingProviderId) =>
      taggingProviderService.getById(taggingProviderId)
        .flatMap {
          case Some(taggingProvider) => Ok(taggingProvider)
          case _ => NotFound()
        }
  }
}

object TaggingProviderJsonCodecs {
  implicit val pictureRequestCodec: Codec[TaggingProviderEntity] = deriveCodec
}
