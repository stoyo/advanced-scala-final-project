package fmi.scala.user

import cats.data.EitherNec
import cats.syntax.either._
import cats.syntax.parallel._

case class LoginResponse(token: String)

case class UserDto(
  email: String,
  password: String
)

sealed trait UserValidationError
case class InvalidEmail(email: String) extends UserValidationError

object UserDto {
  def validate(user: UserDto): EitherNec[UserValidationError, UserDto] = {
    (
      validateEmail(user.email),
      PasswordUtils.hash(user.password).rightNec,
    ).parMapN(UserDto.apply)
  }

  def validateEmail(email: String): EitherNec[UserValidationError, String] = {
    if (email.count(_ == '@') == 1) email.rightNec
    else InvalidEmail(email).leftNec
  }
}
