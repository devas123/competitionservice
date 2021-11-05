package compman.compsrv.query.service.repository

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.model.dto.competition.CompetitionStatus
import compman.compsrv.query.model.ManagedCompetition
import org.mongodb.scala.{MongoClient, MongoCollection, Observable}
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Updates.set
import zio.{Ref, RIO}

object ManagedCompetitionsOperations {
  def test(competitions: Ref[Map[String, ManagedCompetition]]): ManagedCompetitionService[LIO] =
    new ManagedCompetitionService[LIO] {
      override def getManagedCompetitions: LIO[List[ManagedCompetition]] = {
        for { map <- competitions.get } yield map.values.toList
      }

      override def getActiveCompetitions: LIO[List[ManagedCompetition]] = getManagedCompetitions

      override def addManagedCompetition(competition: ManagedCompetition): LIO[Unit] = {
        competitions.update(m => m + (competition.id -> competition))
      }

      override def deleteManagedCompetition(id: String): LIO[Unit] = { competitions.update(m => m - id) }

      override def updateManagedCompetition(c: ManagedCompetition): LIO[Unit] = competitions.update(m =>
        m.updatedWith(c.id)(_.map(_.copy(
          competitionName = c.competitionName,
          startsAt = c.startsAt,
          endsAt = c.endsAt,
          timeZone = c.timeZone,
          status = c.status
        )))
      )
    }

  def live(mongo: MongoClient, name: String)(implicit
    log: CompetitionLogging.Service[LIO]
  ): ManagedCompetitionService[LIO] = new ManagedCompetitionService[LIO] with CommonLiveOperations {


    override def mongoClient: MongoClient = mongo

    override def dbName: String = name

    override def idField: String = "id"


    override def getManagedCompetitions: LIO[List[ManagedCompetition]] = {
      val select = managedCompetitionCollection.find()
      runQuery(select)
    }

    override def getActiveCompetitions: LIO[List[ManagedCompetition]] = {
      val select = managedCompetitionCollection.find().filter(_.status != CompetitionStatus.DELETED)
      runQuery(select)
    }
    override def addManagedCompetition(competition: ManagedCompetition): LIO[Unit] = {
      val insert = managedCompetitionCollection.insertOne(competition)
      for {
        _ <- log.info(insert.toString)
        _ <- RIO.fromFuture(_ => insert.toFuture())
      } yield ()
    }

    override def deleteManagedCompetition(id: String): LIO[Unit] = {
      val delete = managedCompetitionCollection.deleteOne(equal("id", id))
      for {
        _ <- log.info(delete.toString)
        _ <- RIO.fromFuture(_ => delete.toFuture())
      } yield ()
    }

    override def updateManagedCompetition(competition: ManagedCompetition): LIO[Unit] = {

      val update = managedCompetitionCollection.updateOne(
        equal("id", competition.id),
        Seq(
          set("competitionName", competition.competitionName),
          set("eventsTopic", competition.eventsTopic),
          set("creatorId", competition.creatorId),
          set("createdAt", competition.createdAt),
          set("startsAt", competition.startsAt),
          set("endsAt", competition.endsAt),
          set("timeZone", competition.timeZone),
          set("status", competition.status)
        )
      )
      for { _ <- RIO.fromFuture(_ => update.toFuture()) } yield ()
    }
  }

  private def runQuery(select: Observable[ManagedCompetition])(implicit log: CompetitionLogging.Service[LIO]) = {
    for {
      _   <- log.info(select.toString)
      res <- RIO.fromFuture(_ => select.toFuture())
    } yield res.toList
  }

  trait ManagedCompetitionService[F[+_]] {
    def getManagedCompetitions: F[List[ManagedCompetition]]
    def getActiveCompetitions: F[List[ManagedCompetition]]
    def addManagedCompetition(competition: ManagedCompetition): F[Unit]
    def updateManagedCompetition(c: ManagedCompetition): F[Unit]
    def deleteManagedCompetition(id: String): F[Unit]
  }

  object ManagedCompetitionService {
    def apply[F[+_]](implicit F: ManagedCompetitionService[F]): ManagedCompetitionService[F] = F
  }

  def getManagedCompetitions[F[+_]: CompetitionLogging.Service: ManagedCompetitionService]
    : F[List[ManagedCompetition]] = ManagedCompetitionService[F].getManagedCompetitions
  def getActiveCompetitions[F[+_]: CompetitionLogging.Service: ManagedCompetitionService]: F[List[ManagedCompetition]] =
    ManagedCompetitionService[F].getActiveCompetitions
  def addManagedCompetition[F[+_]: CompetitionLogging.Service: ManagedCompetitionService](
    competition: ManagedCompetition
  ): F[Unit] = ManagedCompetitionService[F].addManagedCompetition(competition)
  def updateManagedCompetition[F[+_]: CompetitionLogging.Service: ManagedCompetitionService](
    c: ManagedCompetition
  ): F[Unit] = ManagedCompetitionService[F].updateManagedCompetition(c)
  def deleteManagedCompetition[F[+_]: CompetitionLogging.Service: ManagedCompetitionService](id: String): F[Unit] =
    ManagedCompetitionService[F].deleteManagedCompetition(id)
}
