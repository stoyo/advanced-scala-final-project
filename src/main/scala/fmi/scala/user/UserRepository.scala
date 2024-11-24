package fmi.scala.user

import cats.effect.IO
import fmi.scala.infrastructure.db.DoobieDatabase.DbTransactor
import cats.syntax.functor._
import doobie.implicits._
import doobie.postgres.sqlstate

case class UserEntity(id: Int, email: String, passwordHash: String)

class UserRepository(dbTransactor: DbTransactor) {
  def retrieveUserById(id: Int): IO[Option[UserEntity]] = {
    sql"""
      SELECT id, email, password_hash
      FROM "user"
      WHERE id = $id
    """
      .query[UserEntity]
      .option
      .transact(dbTransactor)
  }

  def retrieveUserByEmail(email: String): IO[Option[UserEntity]] = {
    sql"""
      SELECT id, email, password_hash
      FROM "user"
      WHERE email = $email
    """
      .query[UserEntity]
      .option
      .transact(dbTransactor)
  }

  def registerUser(user: UserDto): IO[Either[UserAlreadyExists, UserDto]] = {
    sql"""
      INSERT INTO "user" (email, password_hash)
      VALUES (${user.email}, ${user.password})
    """
      .update
      .run
      .as(user)
      .attemptSomeSqlState {
        case sqlstate.class23.UNIQUE_VIOLATION => UserAlreadyExists(user.email)
      }
      .transact(dbTransactor)
  }
}
