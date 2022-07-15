package compman.compsrv.query.service.repository

import cats.Monad
import cats.implicits._
import cats.effect.IO
import compman.compsrv.query.model.academy.FullAcademyInfo
import org.mongodb.scala.{MongoClient, MongoCollection}
import org.mongodb.scala.model.Filters.{equal, regex}
import org.mongodb.scala.model.Updates.set

import java.util.concurrent.atomic.AtomicReference

object AcademyOperations {
  def test[F[_]: Monad](academies: AtomicReference[Map[String, FullAcademyInfo]]): AcademyService[F] =
    new AcademyService[F] {
      override def getAcademies(
        searchString: Option[String],
        pagination: Option[Pagination]
      ): F[(List[FullAcademyInfo], Pagination)] = {
        val map = academies.get()
        Monad[F].pure((map.values.toList, Pagination(0, 0, 0)))
      }

      override def addAcademy(competition: FullAcademyInfo): F[Unit] = {
        Monad[F].pure(academies.updateAndGet(m => m + (competition.id -> competition))).void
      }

      override def deleteAcademy(id: String): F[Unit] = Monad[F].pure { academies.updateAndGet(m => m - id) }.void
      override def getAcademy(id: String): F[Option[FullAcademyInfo]] = Monad[F].pure { academies.get.get(id) }

      override def updateAcademy(c: FullAcademyInfo): F[Unit] = Monad[F]
        .pure(academies.updateAndGet(m => m.updatedWith(c.id)(_.map(_.copy(name = c.name, coaches = c.coaches))))).void
    }

  def live(mongo: MongoClient, name: String): AcademyService[IO] =
    new AcademyService[IO] with CommonLiveOperations {

      override def mongoClient: MongoClient = mongo

      override def dbName: String = name

      override def getAcademies(
        searchString: Option[String],
        pagination: Option[Pagination]
      ): IO[(List[FullAcademyInfo], Pagination)] = {
        val drop = pagination.map(_.offset).getOrElse(0)
        val take = pagination.map(_.maxResults).getOrElse(0)
        val call = searchString.map(str =>
          (collection: MongoCollection[FullAcademyInfo]) => collection.find(regex("name", s".*$str.*", "im"))
        ).getOrElse((collection: MongoCollection[FullAcademyInfo]) => collection.find())
        for {
          collection <- academyCollection
          select = call(collection).skip(drop).limit(take)
          total  = IO.fromFuture(IO(collection.countDocuments().toFuture()))
          res <- selectWithPagination(select, pagination, total)
        } yield res
      }

      override def addAcademy(academy: FullAcademyInfo): IO[Unit] =
        insertElement(academyCollection)(academy.id, academy)

      override def deleteAcademy(id: String): IO[Unit] = deleteByField(academyCollection)(id)

      override def updateAcademy(academy: FullAcademyInfo): IO[Unit] = {
        for {
          collection <- academyCollection
          update = collection.updateMany(
            equal(idField, academy.id),
            Seq(setOption("name", academy.name), set("coaches", academy.coaches))
          )
          _ <- IO.fromFuture(IO(update.toFuture()))
        } yield ()
      }

      override def getAcademy(id: String): IO[Option[FullAcademyInfo]] = for {
        collection <- academyCollection
        select = collection.find(equal(idField, id))
        res <- selectOne(select)
      } yield res

    }

  trait AcademyService[F[_]] {
    def getAcademies(
      searchString: Option[String],
      pagination: Option[Pagination]
    ): F[(List[FullAcademyInfo], Pagination)]
    def addAcademy(competition: FullAcademyInfo): F[Unit]
    def updateAcademy(c: FullAcademyInfo): F[Unit]
    def deleteAcademy(id: String): F[Unit]
    def getAcademy(id: String): F[Option[FullAcademyInfo]]
  }

  object AcademyService {
    def apply[F[_]](implicit F: AcademyService[F]): AcademyService[F] = F
  }

  def getAcademy[F[_]: AcademyService](id: String): F[Option[FullAcademyInfo]] = AcademyService[F].getAcademy(id)
  def getAcademies[F[_]: AcademyService](
    searchString: Option[String],
    pagination: Option[Pagination]
  ): F[(List[FullAcademyInfo], Pagination)] = AcademyService[F].getAcademies(searchString, pagination)
  def addAcademy[F[_]: AcademyService](competition: FullAcademyInfo): F[Unit] = AcademyService[F]
    .addAcademy(competition)
  def updateAcademy[F[_]: AcademyService](c: FullAcademyInfo): F[Unit] = AcademyService[F].updateAcademy(c)
  def deleteAcademy[F[_]: AcademyService](id: String): F[Unit]         = AcademyService[F].deleteAcademy(id)
}
