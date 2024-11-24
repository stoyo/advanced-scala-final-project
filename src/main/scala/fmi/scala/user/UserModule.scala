package fmi.scala.user

import cats.effect.IO
import cats.effect.kernel.Resource
import fmi.scala.infrastructure.JwtService
import fmi.scala.infrastructure.db.DoobieDatabase.DbTransactor
import org.http4s.HttpRoutes
import org.http4s.server.AuthMiddleware

case class UserModule(
  userRepository: UserRepository,
  userService: UserService,
  authMiddleware: AuthMiddleware[IO, AuthenticatedUser],
  routes: HttpRoutes[IO]
)

object UserModule {
  def apply(dbTransactor: DbTransactor, jwtService: JwtService): Resource[IO, UserModule] = {
    val userRepository = new UserRepository(dbTransactor)
    val userService = new UserService(userRepository)
    val authenticationUtils = new AuthenticationUtils(jwtService, userRepository)
    val userRouter = new UserRouter(userService, authenticationUtils)

    Resource.pure(UserModule(
      userRepository,
      userService,
      authenticationUtils.authMiddleware,
      userRouter.nonAuthenticatedRoutes,
    ))
  }
}
