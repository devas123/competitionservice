package compman.compsrv.query.service.repository

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.model.dto.competition.CompetitionStatus
import compman.compsrv.query.model.ManagedCompetition
import io.getquill.{CassandraZioContext, CassandraZioSession, SnakeCase}
import io.getquill.context.cassandra.encoding.{Decoders, Encoders}
import zio.{Has, Ref, ZIO, ZLayer}
import zio.logging.Logging

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

  def live(cassandraZioSession: CassandraZioSession)(implicit
    log: CompetitionLogging.Service[LIO]
  ): ManagedCompetitionService[LIO] = new ManagedCompetitionService[LIO] {
    private lazy val ctx =
      new CassandraZioContext(SnakeCase) with CustomDecoders with CustomEncoders with Encoders with Decoders

    import ctx._

    private final val managedCompetitionsByStatus = "managed_competition_by_status"

    override def getManagedCompetitions: LIO[List[ManagedCompetition]] = {
      val select = quote { query[ManagedCompetition] }
      for {
        _   <- log.info(select.toString)
        res <- run(select).provide(Has(cassandraZioSession))
      } yield res
    }

    override def getActiveCompetitions: LIO[List[ManagedCompetition]] = {
      val select = quote {
        querySchema[ManagedCompetition](managedCompetitionsByStatus).filter(c =>
          liftQuery(List(
            CompetitionStatus.CREATED,
            CompetitionStatus.STARTED,
            CompetitionStatus.STOPPED,
            CompetitionStatus.PAUSED,
            CompetitionStatus.PUBLISHED,
            CompetitionStatus.UNPUBLISHED
          )).contains(c.status)
        )
      }
      for {
        _   <- log.info(select.toString)
        res <- run(select).provide(Has(cassandraZioSession))
      } yield res
    }
    override def addManagedCompetition(competition: ManagedCompetition): LIO[Unit] = {
      val insert = quote { query[ManagedCompetition].insert(liftCaseClass(competition)) }
      val insert2 =
        quote { querySchema[ManagedCompetition](managedCompetitionsByStatus).insert(liftCaseClass(competition)) }
      (for {
        _ <- log.info(insert.toString)
        _ <- run(insert)
        _ <- run(insert2)
      } yield ()).provideSomeLayer[Logging](ZLayer.succeed(cassandraZioSession))
    }

    private val byId = quote { cid: String => query[ManagedCompetition].filter(_.id == cid) }

    private val delete2 = quote { (id: String, status: CompetitionStatus) =>
      querySchema[ManagedCompetition](managedCompetitionsByStatus)
        .filter(cmpD2 => cmpD2.status == status && cmpD2.id == id).delete
    }

    private val insert2 = quote { competition1: ManagedCompetition =>
      querySchema[ManagedCompetition](managedCompetitionsByStatus).insert(competition1)
    }

    override def deleteManagedCompetition(id: String): LIO[Unit] = {
      val delete = quote { byId(lift(id)).update(_.status -> lift(CompetitionStatus.DELETED)) }

      val select = quote { byId(lift(id)) }

      for {
        _        <- log.info(delete.toString)
        existing <- run(select).map(_.headOption)
        _        <- run(delete)
        _ <- existing match {
          case Some(cada) =>
            val newC = cada.copy(status = CompetitionStatus.DELETED)
            run(quote { delete2(lift(cada.id), lift(cada.status)) }) *> run(quote { insert2(liftCaseClass(newC)) })
          case None => ZIO.unit
        }
      } yield ()
    }.provideSomeLayer[Logging](ZLayer.succeed(cassandraZioSession))

    override def updateManagedCompetition(competition: ManagedCompetition): LIO[Unit] = {
      val layer = ZLayer.succeed(cassandraZioSession)

      val select = quote { byId(lift(competition.id)) }

      val update = quote {
        byId(lift(competition.id)).update(
          _.competitionName -> lift(competition.competitionName),
          _.eventsTopic     -> lift(competition.eventsTopic),
          _.creatorId       -> lift(competition.creatorId),
          _.createdAt       -> lift(competition.createdAt),
          _.startsAt        -> lift(competition.startsAt),
          _.endsAt          -> lift(competition.endsAt),
          _.timeZone        -> lift(competition.timeZone),
          _.status          -> lift(competition.status)
        )
      }


      (for {
        existing <- run(select).map(_.headOption)
        _ <- existing match {
          case Some(e) => run(quote { delete2(lift(e.id), lift(e.status)) })
          case None    => ZIO.unit
        }
        _ <- run(quote { insert2(liftCaseClass(competition)) })
        _ <- run(update)
      } yield ()).provideSomeLayer[Logging](layer)
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
