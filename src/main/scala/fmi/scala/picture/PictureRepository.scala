package fmi.scala.picture

import cats.Show
import cats.data.NonEmptyList
import cats.effect.IO
import doobie.implicits._
import doobie.{Fragments, _}
import fmi.scala.infrastructure.db.DoobieDatabase.DbTransactor
import fmi.scala.tagging.provider.TaggingProviderEntity
import fmi.scala.user.{AuthenticatedUser, UserEntity}
import org.postgresql.jdbc.PgArray

import scala.util.{Failure, Success, Try}

case class PictureEntity(id: Int, user: UserEntity, path: String)

class PictureRepository(dbTransactor: DbTransactor) {

  implicit val showPGarray: Show[PgArray] = Show.show(_.toString.take(250))

  implicit val listOfStringsGet: Get[List[String]] =
    Get.Advanced.other[PgArray](NonEmptyList.of("json")).temap[List[String]] { o =>
      Try(o.getArray.asInstanceOf[Array[String]]) match {
        case Success(stringsArray) => Right(stringsArray.toList)
        case Failure(exception) => Left(exception.getMessage)
      }
    }

  def getAll(
    user: AuthenticatedUser,
    top: Int,
    skip: Int
  ): IO[List[(Int, String, List[String], List[String])]] =
    sql"""
         SELECT
           p.id,
           p.path,
           array_remove(array_agg(t.value ORDER BY p.id, p.path), NULL),
           array_remove(array_agg(tp.title ORDER BY p.id, p.path), NULL)
         FROM picture AS p
         LEFT JOIN tag AS t ON p.id = t.picture_id
         LEFT JOIN tagging_provider AS tp ON t.tagging_provider_id = tp.id
         WHERE p.user_id = ${user.id}
         GROUP BY p.id, p.path
         ORDER BY p.id DESC
         LIMIT $top
         OFFSET $skip
       """
      .query[(Int, String, List[String], List[String])]
      .to[List]
      .transact(dbTransactor)

  def get(
    user: AuthenticatedUser,
    pictureId: Int
  ): IO[Option[(Int, String, List[String], List[String])]] =
    sql"""
         SELECT
           p.id,
           p.path,
           array_remove(array_agg(t.value ORDER BY p.id, p.path), NULL),
           array_remove(array_agg(tp.title ORDER BY p.id, p.path), NULL)
         FROM picture AS p
         LEFT JOIN tag AS t ON p.id = t.picture_id
         LEFT JOIN tagging_provider AS tp ON t.tagging_provider_id = tp.id
         WHERE p.user_id = ${user.id} AND p.id = $pictureId
         GROUP BY p.id, p.path
       """
      .query[(Int, String, List[String], List[String])]
      .option
      .transact(dbTransactor)

  // CNF: (cat || dog || ...) && (outdoors || nature || ...)
  def getByTags(
    user: AuthenticatedUser,
    andOfOrs: NonEmptyList[NonEmptyList[String]],
    top: Int,
    skip: Int
  ): IO[List[(Int, String, List[String], List[String])]] = {


    val innerQueryStart =
      fr"""
          SELECT DISTINCT picture.id, picture.path
          FROM picture
          INNER JOIN tag ON picture.id = tag.picture_id
          WHERE picture.user_id = ${user.id}
          GROUP BY picture.id, picture.path
          HAVING
        """

    val innerQueryAndClauses =
      Fragments.and(andOfOrs
        .map(ors => Fragments.or(ors
          .map(b => fr"$b = ANY(array_remove(array_agg(tag.value ORDER BY picture.id,picture.path), NULL))")
          .toList : _*))
        .toList : _*)

    val innerQueryEnd =
      fr"""
          ORDER BY picture.id DESC
          LIMIT $top
          OFFSET $skip"""
    val innerQuery = innerQueryStart ++ innerQueryAndClauses ++ innerQueryEnd

    val outerQueryStart =
      fr"""
           SELECT
             p.id,
             p.path,
             array_remove(array_agg(t.value ORDER BY p.id, p.path), NULL),
             array_remove(array_agg(tp.title ORDER BY p.id, p.path), NULL)
           FROM (
        """

    val outerQueryEnd =
      fr"""
           ) AS p (id, path)
           LEFT JOIN tag AS t ON p.id = t.picture_id
           LEFT JOIN tagging_provider AS tp ON t.tagging_provider_id = tp.id
           GROUP BY p.id, p.path
           ORDER BY p.id DESC
        """

    (outerQueryStart ++ innerQuery ++ outerQueryEnd)
      .queryWithLogHandler[(Int, String, List[String], List[String])](LogHandler.jdkLogHandler)
      .to[List]
      .transact(dbTransactor)
  }

  def storePicture(
    pictureName: String,
    tags: Seq[(TaggingProviderEntity, Seq[Tag])],
    user: AuthenticatedUser
  ): IO[Either[Nothing, Int]] =
    (for {
      id <- sql"INSERT INTO picture (user_id, path) VALUES (${user.id}, $pictureName)"
        .update
        .withUniqueGeneratedKeys[Int]("id")

      insertTagsQuery = """INSERT INTO tag (picture_id, tagging_provider_id, "value") VALUES (?, ?, ?)"""

      _ <- Update[(Int, Int, String)](insertTagsQuery)
        .updateMany(tags.flatMap(tuple => tuple._2.map(tag => (id, tuple._1.id, tag.value))))
    } yield id)
      .transact(dbTransactor)
      .map(Right(_))
}
