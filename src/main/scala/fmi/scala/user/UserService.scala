package fmi.scala.user

import cats.data.{EitherT, NonEmptyChain}
import cats.effect.IO
import cats.implicits._

class UserService(userRepository: UserRepository) {
  def registerUser(user: UserDto): IO[Either[RegistrationError, UserDto]] =
    (for {
      user <- EitherT.fromEither[IO](
        UserDto
          .validate(user)
          .leftMap(UserFieldsError)
          .leftWiden[RegistrationError]
      )
      _ <- EitherT(userRepository.registerUser(user)).leftWiden[RegistrationError]
  } yield user).value

  def login(userDto: UserDto): IO[Option[UserEntity]] =
    userRepository.retrieveUserByEmail(userDto.email).map {
      case Some(userEntity) =>
        if (PasswordUtils.checkPasswords(userDto.password, userEntity.passwordHash)) Some(userEntity)
        else None
      case _ => None
    }
}

sealed trait RegistrationError
case class UserFieldsError(registrationErrors: NonEmptyChain[UserValidationError]) extends RegistrationError
case class UserAlreadyExists(email: String) extends RegistrationError
