package compman.compsrv.query.service.repository

import cats.implicits._
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.query.model.{Fight, FightStartTimeUpdate}
import zio.{Has, Ref, ZIO}
import zio.interop.catz._

trait FightUpdateOperations[F[+_]] {
  def addFight(fight: Fight): F[Unit]
  def addFights(fights: List[Fight]): F[Unit]
  def updateFight(fight: Fight): F[Unit]
  def updateFightScores(fights: List[Fight]): F[Unit]
  def updateFightStartTime(fights: List[FightStartTimeUpdate]): F[Unit]
  def removeFight(competitionId: String)(id: String): F[Unit]
  def removeFights(competitionId: String)(ids: List[String]): F[Unit]
  def removeFightsForCategory(competitionId: String)(categoryId: String): F[Unit]
}

object FightUpdateOperations {
  def apply[F[+_]](implicit F: FightUpdateOperations[F]): FightUpdateOperations[F] = F

  def test(fights: Option[Ref[Map[String, Fight]]] = None): FightUpdateOperations[LIO] = new FightUpdateOperations[LIO]
    with CommonTestOperations {
    override def removeFightsForCategory(competitionId: String)(categoryId: String): LIO[Unit] = fights
      .map(_.update(fs => fs.filter(f => f._2.categoryId != categoryId))).getOrElse(ZIO.unit)

    override def updateFightStartTime(fights: List[FightStartTimeUpdate]): LIO[Unit] = updateFightScores(List.empty)
    override def addFight(fight: Fight): LIO[Unit] = add(fights)(fight.id)(Some(fight))

    override def addFights(fights: List[Fight]): LIO[Unit] = fights.traverse(addFight).map(_ => ())

    override def updateFight(fight: Fight): LIO[Unit] = update(fights)(fight.id)(_ => fight)

    override def updateFightScores(fights: List[Fight]): LIO[Unit] = fights.traverse(updateFight).map(_ => ())

    override def removeFight(competitionId: String)(id: String): LIO[Unit] = remove(fights)(id)

    override def removeFights(competitionId: String)(ids: List[String]): LIO[Unit] = ids
      .traverse(removeFight(competitionId)).map(_ => ())
  }

  def live(
    cassandraZioSession: CassandraZioSession
  )(implicit log: CompetitionLogging.Service[LIO]): FightUpdateOperations[LIO] = new FightUpdateOperations[LIO] {
    private lazy val ctx =
      new CassandraZioContext(SnakeCase) with CustomDecoders with CustomEncoders with Encoders with Decoders
    import ctx._

    override def addFight(fight: Fight): LIO[Unit] = {
      val statement = quote { query[Fight].insert(liftCaseClass(fight)) }
      for {
        _ <- log.info(statement.toString)
        _ <- run(statement).provide(Has(cassandraZioSession))
      } yield ()
    }

    override def addFights(fights: List[Fight]): LIO[Unit] = {
      val statement = quote { liftQuery(fights).foreach(fight1 => query[Fight].insert(fight1)) }
      for {
        _ <- log.info(statement.toString)
        _ <- run(statement).provide(Has(cassandraZioSession))
      } yield ()
    }

    override def updateFight(fight: Fight): LIO[Unit] = {
      val statement = quote { query[Fight].filter(_.id == lift(fight.id)).update(liftCaseClass(fight)) }
      for {
        _ <- log.info(statement.toString)
        _ <- run(statement).provide(Has(cassandraZioSession))
      } yield ()
    }

    override def updateFightScores(fights: List[Fight]): LIO[Unit] = {
      val statement = quote {
        liftQuery(fights).foreach(fight2 =>
          query[Fight].filter(f =>
            f.id == fight2.id && f.competitionId == fight2.competitionId && f.categoryId == fight2.categoryId
          ).update(f => f.scores -> fight2.scores, _.status -> fight2.status)
        )
      }
      for {
        _ <- log.info(statement.toString)
        _ <- run(statement).provide(Has(cassandraZioSession))
      } yield ()
    }

    override def removeFight(competitionId: String)(id: String): LIO[Unit] = {
      val statement = quote {
        query[Fight].filter(fight3 => fight3.competitionId == lift(competitionId) && fight3.id == lift(id)).delete
      }
      for {
        _ <- log.info(statement.toString)
        _ <- run(statement).provide(Has(cassandraZioSession))
      } yield ()
    }

    override def removeFights(competitionId: String)(ids: List[String]): LIO[Unit] = {
      val statement = quote {
        liftQuery(ids).foreach(id =>
          query[Fight].filter(fight4 => fight4.competitionId == lift(competitionId) && fight4.id == id).delete
        )
      }
      for {
        _ <- log.info(statement.toString)
        _ <- run(statement).provide(Has(cassandraZioSession))
      } yield ()

    }

    override def removeFightsForCategory(competitionId: String)(categoryId: String): LIO[Unit] = {
      val statement = quote {
        query[Fight].filter(fight => fight.competitionId == lift(competitionId) && fight.categoryId == lift(categoryId))
          .delete
      }
      for {
        _ <- log.info(statement.toString)
        _ <- run(statement).provide(Has(cassandraZioSession))
      } yield ()
    }

    override def updateFightStartTime(fights: List[FightStartTimeUpdate]): LIO[Unit] = {
      {
        val statement = quote {
          liftQuery(fights).foreach(fight2 =>
            query[Fight].filter(f =>
              f.id == fight2.id && f.competitionId == fight2.competitionId && f.categoryId == fight2.categoryId
            ).update(
              _.matId           -> fight2.matId,
              _.matName         -> fight2.matName,
              _.matOrder        -> fight2.matOrder,
              _.numberOnMat     -> fight2.numberOnMat,
              _.periodId        -> fight2.periodId,
              _.startTime       -> fight2.startTime,
              _.invalid         -> fight2.invalid,
              _.scheduleEntryId -> fight2.scheduleEntryId
            )
          )
        }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement).provide(Has(cassandraZioSession))
        } yield ()
      }
    }
  }
}
