package fmi.scala.tagging.provider

import cats.effect.IO
import doobie.implicits._
import fmi.scala.infrastructure.db.DoobieDatabase.DbTransactor

case class TaggingProviderEntity(id: Int, title: String, description: Option[String])

class TaggingProviderRepository(dbTransactor: DbTransactor) {

  def getAll: IO[List[TaggingProviderEntity]] =
    sql"""
      SELECT id, title, description
      FROM tagging_provider
    """
      .query[TaggingProviderEntity]
      .to[List]
      .transact(dbTransactor)

  def retrieveById(id: Int): IO[Option[TaggingProviderEntity]] =
    sql"""
      SELECT id, title, description
      FROM tagging_provider
      WHERE id = $id
    """
      .query[TaggingProviderEntity]
      .option
      .transact(dbTransactor)

  def retrieveByTitle(name: String): IO[Option[TaggingProviderEntity]] =
    sql"""
      SELECT id, title, description
      FROM tagging_provider AS tp
      WHERE LOWER(tp.title) LIKE '%' || $name || '%'
      LIMIT 1
    """
      .query[TaggingProviderEntity]
      .option
      .transact(dbTransactor)
}
