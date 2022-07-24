package compman.compsrv.query.service.repository

import cats.Monad
import cats.effect.IO
import cats.implicits.toFunctorOps
import compman.compsrv.query.model.ManagedCompetition
import compservice.model.protobuf.model.CompetitionStatus
import org.mongodb.scala.MongoClient
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Updates.set

import java.util.concurrent.atomic.AtomicReference

object ManagedCompetitionsOperations {
  def test[F[+_]: Monad](competitions: AtomicReference[Map[String, ManagedCompetition]]): ManagedCompetitionService[F] =
    new ManagedCompetitionService[F] {
      override def getManagedCompetitions: F[List[ManagedCompetition]] = {
        Monad[F].pure(competitions.get().values.toList)
      }

      override def getActiveCompetitions: F[List[ManagedCompetition]] = getManagedCompetitions

      override def addManagedCompetition(competition: ManagedCompetition): F[Unit] = {
        Monad[F].pure(competitions.updateAndGet(m => m + (competition.id -> competition))).void
      }

      override def deleteManagedCompetition(id: String): F[Unit] = Monad[F]
        .pure { competitions.updateAndGet(m => m - id) }.void

      override def updateManagedCompetition(c: ManagedCompetition): F[Unit] = Monad[F]
        .pure(competitions.updateAndGet(m =>
          m.updatedWith(c.id)(_.map(_.copy(
            competitionName = c.competitionName,
            startsAt = c.startsAt,
            endsAt = c.endsAt,
            timeZone = c.timeZone,
            status = c.status
          )))
        )).void
    }

  def live(mongo: MongoClient, name: String): ManagedCompetitionService[IO] = new ManagedCompetitionService[IO]
    with CommonLiveOperations {

    override def mongoClient: MongoClient = mongo

    override def dbName: String = name

    override def getManagedCompetitions: IO[List[ManagedCompetition]] = {
      for {
        collection <- managedCompetitionCollection
        select = collection.find()
        res <- runQuery(select)
      } yield res
    }

    override def getActiveCompetitions: IO[List[ManagedCompetition]] = {
      for {
        collection <- managedCompetitionCollection
        select = collection.find().filter(_.status != CompetitionStatus.DELETED)
        res <- runQuery(select)
      } yield res
    }
    override def addManagedCompetition(competition: ManagedCompetition): IO[Unit] =
      insertElement(managedCompetitionCollection)(competition.id, competition)

    override def deleteManagedCompetition(id: String): IO[Unit] = deleteByField(managedCompetitionCollection)(id)

    override def updateManagedCompetition(competition: ManagedCompetition): IO[Unit] = {
      for {
        collection <- managedCompetitionCollection
        update = collection.updateMany(
          equal(idField, competition.id),
          Seq(
            setOption("competitionName", competition.competitionName),
            set("eventsTopic", competition.eventsTopic),
            setOption("creatorId", competition.creatorId),
            set("createdAt", competition.createdAt),
            set("startsAt", competition.startsAt),
            setOption("endsAt", competition.endsAt),
            set("timeZone", competition.timeZone),
            set("status", competition.status)
          )
        )
        _ <- IO.fromFuture(IO(update.toFuture()))
      } yield ()
    }
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

  def getManagedCompetitions[F[+_]: ManagedCompetitionService]: F[List[ManagedCompetition]] =
    ManagedCompetitionService[F].getManagedCompetitions
  def getActiveCompetitions[F[+_]: ManagedCompetitionService]: F[List[ManagedCompetition]] =
    ManagedCompetitionService[F].getActiveCompetitions
  def addManagedCompetition[F[+_]: ManagedCompetitionService](competition: ManagedCompetition): F[Unit] =
    ManagedCompetitionService[F].addManagedCompetition(competition)
  def updateManagedCompetition[F[+_]: ManagedCompetitionService](c: ManagedCompetition): F[Unit] =
    ManagedCompetitionService[F].updateManagedCompetition(c)
  def deleteManagedCompetition[F[+_]: ManagedCompetitionService](id: String): F[Unit] = ManagedCompetitionService[F]
    .deleteManagedCompetition(id)
}
