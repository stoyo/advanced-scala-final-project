package fmi.scala.user

import cats.data.{EitherT, Kleisli, OptionT}
import cats.effect.IO
import fmi.scala.infrastructure.JwtService
import fmi.scala.picture.ErrorResponse
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.dsl.io._
import org.http4s.headers.Authorization
import org.http4s.server.AuthMiddleware
import org.http4s.{AuthedRoutes, Request, Response}

case class AuthenticatedUser(id: Int, email: String)

class AuthenticationUtils(jwtService: JwtService, userRepository: UserRepository) {
  import UserJsonCodecs._
  import org.http4s.circe.CirceEntityCodec._

  implicit val errorResponseCodec: Codec[ErrorResponse] = deriveCodec

  def responseWithUserToken(user: UserEntity): IO[Response[IO]] =
    Ok(LoginResponse(jwtService.generateToken(user.id.toString)))

  private val authUser: Kleisli[IO, Request[IO], Either[String, AuthenticatedUser]] =
    Kleisli { request =>
      val userId = for {
        header <- request.headers.get[Authorization].toRight("Couldn't find an Authorization header")
        id <- jwtService.getUserId(header).toRight("Invalid token")
      } yield id

      (for {
        userId <- EitherT.fromEither[IO](userId)
        user <- EitherT(userRepository.retrieveUserById(userId).map(_.toRight("User not found")))
      } yield AuthenticatedUser(user.id, user.email)).value
    }

  private val onFailure: AuthedRoutes[String, IO] =
    Kleisli(req => OptionT.liftF(Forbidden(ErrorResponse(Seq(req.context)))))

  val authMiddleware: AuthMiddleware[IO, AuthenticatedUser] = AuthMiddleware(authUser, onFailure)
}
