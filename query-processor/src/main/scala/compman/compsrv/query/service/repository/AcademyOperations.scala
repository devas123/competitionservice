package compman.compsrv.query.service.repository

import com.mongodb.client.model.ReplaceOptions
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.query.model.academy.FullAcademyInfo
import org.mongodb.scala.{MongoClient, Observable}
import org.mongodb.scala.model.Filters
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Updates.set
import zio.{Ref, RIO}

object AcademyOperations {
  def test(competitions: Ref[Map[String, FullAcademyInfo]]): AcademyService[LIO] =
    new AcademyService[LIO] {
      override def getAcademies: LIO[List[FullAcademyInfo]] = {
        for { map <- competitions.get } yield map.values.toList
      }

      override def addAcademy(competition: FullAcademyInfo): LIO[Unit] = {
        competitions.update(m => m + (competition.id -> competition))
      }

      override def deleteAcademy(id: String): LIO[Unit] = { competitions.update(m => m - id) }

      override def updateAcademy(c: FullAcademyInfo): LIO[Unit] = competitions.update(m =>
        m.updatedWith(c.id)(_.map(_.copy(
          name = c.name,
          coaches = c.coaches
        )))
      )
    }

  def live(mongo: MongoClient, name: String): AcademyService[LIO] = new AcademyService[LIO] with CommonLiveOperations {

    override def mongoClient: MongoClient = mongo

    override def dbName: String = name

    override def getAcademies: LIO[List[FullAcademyInfo]] = {
      for {
        collection <- academyCollection
        select = collection.find()
        res <- runQuery(select)
      } yield res
    }

    override def addAcademy(academy: FullAcademyInfo): LIO[Unit] = insertElement(academyCollection)(academy.id, academy)

    override def deleteAcademy(id: String): LIO[Unit] = deleteById(academyCollection)(id)


    override def updateAcademy(academy: FullAcademyInfo): LIO[Unit] = {
      for {
        collection <- academyCollection
        update = collection.updateMany(
          equal(idField, academy.id),
          Seq(
            setOption("name", academy.name),
            set("coaches", academy.coaches)
          )
        )
        _ <- RIO.fromFuture(_ => update.toFuture())
      } yield ()
    }
  }

  private def runQuery(select: Observable[FullAcademyInfo]) = {
    for { res <- RIO.fromFuture(_ => select.toFuture()) } yield res.toList
  }

  trait AcademyService[F[+_]] {
    def getAcademies: F[List[FullAcademyInfo]]
    def addAcademy(competition: FullAcademyInfo): F[Unit]
    def updateAcademy(c: FullAcademyInfo): F[Unit]
    def deleteAcademy(id: String): F[Unit]
  }

  object AcademyService {
    def apply[F[+_]](implicit F: AcademyService[F]): AcademyService[F] = F
  }

  def getAcademies[F[+_]: AcademyService]
    : F[List[FullAcademyInfo]] = AcademyService[F].getAcademies
  def addAcademy[F[+_]: AcademyService](
    competition: FullAcademyInfo
  ): F[Unit] = AcademyService[F].addAcademy(competition)
  def updateAcademy[F[+_]: AcademyService](
    c: FullAcademyInfo
  ): F[Unit] = AcademyService[F].updateAcademy(c)
  def deleteAcademy[F[+_]: AcademyService](id: String): F[Unit] =
    AcademyService[F].deleteAcademy(id)
}
