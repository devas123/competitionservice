package compman.compsrv.query.service.repository

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.model.dto.competition.CompetitionStatus
import compman.compsrv.query.model.ManagedCompetition
import io.getquill.{CassandraZioContext, CassandraZioSession, SnakeCase}
import io.getquill.context.cassandra.encoding.{Decoders, Encoders}
import zio.{Has, Ref}

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
    }

  def live(cassandraZioSession: CassandraZioSession)(implicit
    log: CompetitionLogging.Service[LIO]
  ): ManagedCompetitionService[LIO] = new ManagedCompetitionService[LIO] {
    private lazy val ctx =
      new CassandraZioContext(SnakeCase) with CustomDecoders with CustomEncoders with Encoders with Decoders

    import ctx._

    override def getManagedCompetitions: LIO[List[ManagedCompetition]] = {
      val select = quote { query[ManagedCompetition] }
      for {
        _   <- log.info(select.toString)
        res <- run(select).provide(Has(cassandraZioSession))
      } yield res
    }

    override def getActiveCompetitions: LIO[List[ManagedCompetition]] = {
      val select = quote {
        query[ManagedCompetition].filter(c =>
          liftQuery(List(
            CompetitionStatus.CREATED,
            CompetitionStatus.STARTED,
            CompetitionStatus.STOPPED,
            CompetitionStatus.PAUSED,
            CompetitionStatus.PUBLISHED,
            CompetitionStatus.UNPUBLISHED
          )).contains(c.status)
        ).allowFiltering
      }
      for {
        _   <- log.info(select.toString)
        res <- run(select).provide(Has(cassandraZioSession))
      } yield res
    }
    override def addManagedCompetition(competition: ManagedCompetition): LIO[Unit] = {
      val insert = quote { query[ManagedCompetition].insert(liftCaseClass(competition)) }
      for {
        _ <- log.info(insert.toString)
        _ <- run(insert).provide(Has(cassandraZioSession))
      } yield ()
    }

    override def deleteManagedCompetition(id: String): LIO[Unit] = {
      val delete =
        quote { query[ManagedCompetition].filter(_.id == lift(id)).update(_.status -> lift(CompetitionStatus.DELETED)) }
      for {
        _ <- log.info(delete.toString)
        _ <- run(delete).provide(Has(cassandraZioSession))
      } yield ()
    }
  }

  trait ManagedCompetitionService[F[+_]] {
    def getManagedCompetitions: F[List[ManagedCompetition]]
    def getActiveCompetitions: F[List[ManagedCompetition]]
    def addManagedCompetition(competition: ManagedCompetition): F[Unit]
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
  def deleteManagedCompetition[F[+_]: CompetitionLogging.Service: ManagedCompetitionService](id: String): F[Unit] =
    ManagedCompetitionService[F].deleteManagedCompetition(id)

}
