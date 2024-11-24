package fmi.scala.user

import cats.effect.IO
import io.circe.Codec
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveConfiguredCodec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.HttpRoutes
import org.http4s.dsl.io._

class UserRouter(userService: UserService, authorizationUtils: AuthenticationUtils) {
  import UserJsonCodecs._
  import org.http4s.circe.CirceEntityCodec._

  def nonAuthenticatedRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> Root / "users" =>
      for {
        user <- req.as[UserDto]
        maybeUser <- userService.registerUser(user)
        response <- maybeUser.fold(errors => BadRequest(errors), _ => Created())
      } yield response

    case req @ POST -> Root / "login" =>
      for {
        user <- req.as[UserDto]
        maybeUser <- userService.login(user)
        response <- maybeUser
          .map { user => authorizationUtils.responseWithUserToken(user) }
          .getOrElse(BadRequest())
      } yield response
  }
}

object UserJsonCodecs {
  implicit val configuration: Configuration = Configuration.default.withDiscriminator("type")

  implicit val userDtoCodec: Codec[UserDto] = deriveCodec

  implicit val loginResponseCodec: Codec[LoginResponse] = deriveCodec
  implicit val authenticatedUserCodec: Codec[AuthenticatedUser] = deriveCodec

  implicit val userValidationErrorCodec: Codec[UserValidationError] = deriveConfiguredCodec
  implicit val registrationErrorCodec: Codec[RegistrationError] = deriveConfiguredCodec
}
