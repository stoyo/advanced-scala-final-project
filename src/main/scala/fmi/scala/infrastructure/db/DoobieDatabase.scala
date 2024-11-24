package fmi.scala.infrastructure.db

import cats.effect.IO
import doobie.hikari.HikariTransactor

object DoobieDatabase {
  type DbTransactor = HikariTransactor[IO]
}
