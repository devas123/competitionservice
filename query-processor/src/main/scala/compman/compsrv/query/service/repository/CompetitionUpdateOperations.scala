package compman.compsrv.query.service.repository

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.model.dto.brackets.StageStatus
import compman.compsrv.query.model._
import compman.compsrv.query.model.CompetitionProperties.CompetitionInfoTemplate
import io.getquill._
import io.getquill.context.cassandra.encoding.{Decoders, Encoders}
import zio.Has

trait CompetitionUpdateOperations[F[+_]] {
  def updateRegistrationOpen(competitionId: String)(isOpen: Boolean): F[Unit]
  def addCompetitionProperties(competitionProperties: CompetitionProperties): F[Unit]
  def updateCompetitionProperties(competitionProperties: CompetitionProperties): F[Unit]
  def removeCompetitionProperties(id: String): F[Unit]
  def addCompetitionInfoTemplate(competitionId: String)(competitionInfoTemplate: CompetitionInfoTemplate): F[Unit]
  def removeCompetitionInfoTemplate(competitionId: String): F[Unit]
  def addStage(stageDescriptor: StageDescriptor): F[Unit]
  def updateStage(stageDescriptor: StageDescriptor): F[Unit]
  def updateStageStatus(competitionId: String)(stageId: String, newStatus: StageStatus): F[Unit]
  def addCategory(category: Category): F[Unit]
  def updateCategoryRegistrationStatus(competitionId: String)(id: String, newStatus: Boolean): F[Unit]
  def removeCategory(competitionId: String)(id: String): F[Unit]
  def addCompetitor(competitor: Competitor): F[Unit]
  def updateCompetitor(competitor: Competitor): F[Unit]
  def removeCompetitor(competitionId: String)(id: String): F[Unit]
  def addFight(fight: Fight): F[Unit]
  def addFights(fights: List[Fight]): F[Unit]
  def updateFight(fight: Fight): F[Unit]
  def updateFights(fights: List[Fight]): F[Unit]
  def removeFight(competitionId: String)(id: String): F[Unit]
  def removeFights(competitionId: String)(ids: List[String]): F[Unit]
  def addRegistrationGroup(group: RegistrationGroup): F[Unit]
  def addRegistrationGroups(groups: List[RegistrationGroup]): F[Unit]
  def updateRegistrationGroup(group: RegistrationGroup): F[Unit]
  def updateRegistrationGroups(groups: List[RegistrationGroup]): F[Unit]
  def removeRegistrationGroup(competitionId: String)(id: String): F[Unit]
  def addRegistrationPeriod(period: RegistrationPeriod): F[Unit]
  def updateRegistrationPeriod(period: RegistrationPeriod): F[Unit]
  def updateRegistrationPeriods(periods: List[RegistrationPeriod]): F[Unit]
  def removeRegistrationPeriod(competitionId: String)(id: String): F[Unit]
  def addPeriod(entry: Period): F[Unit]
  def addPeriods(entries: List[Period]): F[Unit]
  def updatePeriods(entries: List[Period]): F[Unit]
  def removePeriod(competitionId: String)(id: String): F[Unit]
  def removePeriods(competitionId: String): F[Unit]
}

object CompetitionUpdateOperations {
  def apply[F[+_]](implicit F: CompetitionUpdateOperations[F]): CompetitionUpdateOperations[F] = F

  def live(cassandraZioSession: CassandraZioSession)(implicit log: CompetitionLogging.Service[LIO]): CompetitionUpdateOperations[LIO] =
    new CompetitionUpdateOperations[LIO] {
      private lazy val ctx =
        new CassandraZioContext(SnakeCase) with CustomDecoders with CustomEncoders with Encoders with Decoders
      import ctx._

      override def updateRegistrationOpen(competitionId: String)(isOpen: Boolean): LIO[Unit] = {
        val statement = quote {
          query[CompetitionProperties].filter(_.id == lift(competitionId)).update(_.registrationOpen -> lift(isOpen))
        }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement).provide(Has(cassandraZioSession))
        } yield ()
      }

      override def addCompetitionProperties(competitionProperties: CompetitionProperties): LIO[Unit] = {
        val statement = quote { query[CompetitionProperties].insert(liftCaseClass(competitionProperties)) }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement).provide(Has(cassandraZioSession))
        } yield ()
      }

      override def updateCompetitionProperties(competitionProperties: CompetitionProperties): LIO[Unit] = {
        val statement = quote {
          query[CompetitionProperties].filter(_.id == lift(competitionProperties.id))
            .update(liftCaseClass(competitionProperties))
        }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement).provide(Has(cassandraZioSession))
        } yield ()
      }

      override def removeCompetitionProperties(id: String): LIO[Unit] = {
        val remove = quote { query[CompetitionProperties].filter(_.id == lift(id)).delete }
        for {
          _ <- log.info(remove.toString)
          _ <- run(remove).provide(Has(cassandraZioSession))
        } yield ()
      }

      override def addCompetitionInfoTemplate(
        competitionId: String
      )(competitionInfoTemplate: CompetitionInfoTemplate): LIO[Unit] = {
        val statement = quote {
          query[CompetitionProperties].filter(_.id == lift(competitionId))
            .update(_.infoTemplate.template -> lift(competitionInfoTemplate.template))
        }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement).provide(Has(cassandraZioSession))
        } yield ()
      }

      override def removeCompetitionInfoTemplate(competitionId: String): LIO[Unit] =
        addCompetitionInfoTemplate(competitionId)(CompetitionInfoTemplate(Array.empty))

      override def addStage(stageDescriptor: StageDescriptor): LIO[Unit] = {
        val statement = quote { query[StageDescriptor].insert(liftCaseClass(stageDescriptor)) }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement).provide(Has(cassandraZioSession))
        } yield ()
      }

      override def updateStage(stageDescriptor: StageDescriptor): LIO[Unit] = {
        val statement = quote {
          query[StageDescriptor].filter(_.id == lift(stageDescriptor.id)).update(liftCaseClass(stageDescriptor))
        }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement).provide(Has(cassandraZioSession))
        } yield ()
      }
      override def updateStageStatus(competitionId: String)(stageId: String, newStatus: StageStatus): LIO[Unit] = {
        val statement =
          quote { query[StageDescriptor].filter(_.id == lift(stageId)).update(_.stageStatus -> lift(newStatus)) }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement).provide(Has(cassandraZioSession))
        } yield ()
      }

      override def addCategory(category: Category): LIO[Unit] = {
        val statement = quote { query[Category].insert(liftCaseClass(category)) }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement).provide(Has(cassandraZioSession))
        } yield ()
      }

      override def updateCategoryRegistrationStatus(
        competitionId: String
      )(id: String, newStatus: Boolean): LIO[Unit] = {
        val statement = quote { query[Category].filter(_.id == lift(id)).update(_.registrationOpen -> lift(newStatus)) }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement).provide(Has(cassandraZioSession))
        } yield ()
      }

      override def removeCategory(competitionId: String)(id: String): LIO[Unit] = {
        val statement =
          quote { query[Category].filter(c => c.competitionId == lift(competitionId) && c.id == lift(id)).delete }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement).provide(Has(cassandraZioSession))
        } yield ()
      }

      override def addCompetitor(competitor: Competitor): LIO[Unit] = {
        val statement = quote { query[Competitor].insert(liftCaseClass(competitor)) }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement).provide(Has(cassandraZioSession))
        } yield ()
      }

      override def updateCompetitor(competitor: Competitor): LIO[Unit] = {
        val statement =
          quote { query[Competitor].filter(_.id == lift(competitor.id)).update(liftCaseClass(competitor)) }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement).provide(Has(cassandraZioSession))
        } yield ()
      }

      override def removeCompetitor(competitionId: String)(id: String): LIO[Unit] = {
        val statement =
          quote { query[Competitor].filter(c => c.competitionId == lift(competitionId) && c.id == lift(id)).delete }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement).provide(Has(cassandraZioSession))
        } yield ()
      }

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

      override def updateFights(fights: List[Fight]): LIO[Unit] = {
        val statement =
          quote { liftQuery(fights).foreach(fight2 => query[Fight].filter(_.id == fight2.id).update(fight2)) }
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

      override def addRegistrationGroup(group: RegistrationGroup): LIO[Unit] = {
        val statement = quote { query[RegistrationGroup].insert(liftCaseClass(group)) }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement).provide(Has(cassandraZioSession))
        } yield ()
      }

      override def addRegistrationGroups(groups: List[RegistrationGroup]): LIO[Unit] = {
        val statement = quote { liftQuery(groups).foreach(group => query[RegistrationGroup].insert(group)) }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement).provide(Has(cassandraZioSession))
        } yield ()
      }

      override def updateRegistrationGroup(group: RegistrationGroup): LIO[Unit] = {
        val statement = quote {
          query[RegistrationGroup]
            .filter(gr => gr.competitionId == lift(group.competitionId) && gr.id == lift(group.id))
            .update(liftCaseClass(group))
        }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement).provide(Has(cassandraZioSession))
        } yield ()
      }

      override def updateRegistrationGroups(groups: List[RegistrationGroup]): LIO[Unit] = {

        val statement = quote {
          liftQuery(groups).foreach { group =>
            query[RegistrationGroup].filter(gr => gr.competitionId == group.competitionId && gr.id == group.id)
              .update(group)
          }
        }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement).provide(Has(cassandraZioSession))
        } yield ()

      }

      override def removeRegistrationGroup(competitionId: String)(id: String): LIO[Unit] = {
        val statement = quote {
          query[RegistrationGroup].filter(gr => gr.competitionId == lift(competitionId) && gr.id == lift(id)).delete
        }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement).provide(Has(cassandraZioSession))
        } yield ()
      }

      override def addRegistrationPeriod(period: RegistrationPeriod): LIO[Unit] = {
        val statement = quote { query[RegistrationPeriod].insert(liftCaseClass(period)) }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement).provide(Has(cassandraZioSession))
        } yield ()
      }

      override def updateRegistrationPeriod(period: RegistrationPeriod): LIO[Unit] = {
        val statement = quote {
          query[RegistrationPeriod]
            .filter(p => p.competitionId == lift(period.competitionId) && p.id == lift(period.id))
            .update(liftCaseClass(period))
        }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement).provide(Has(cassandraZioSession))
        } yield ()
      }

      override def updateRegistrationPeriods(periods: List[RegistrationPeriod]): LIO[Unit] = {
        val statement = quote {
          liftQuery(periods).foreach { period =>
            query[RegistrationPeriod].filter(p => p.competitionId == period.competitionId && p.id == period.id)
              .update(period)
          }

        }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement).provide(Has(cassandraZioSession))
        } yield ()

      }

      override def removeRegistrationPeriod(competitionId: String)(id: String): LIO[Unit] = {
        val statement = quote {
          query[RegistrationPeriod].filter(p => p.competitionId == lift(competitionId) && p.id == lift(id)).delete
        }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement).provide(Has(cassandraZioSession))
        } yield ()
      }

      override def addPeriod(entry: Period): LIO[Unit] = {
        val statement = quote { query[Period].insert(liftCaseClass(entry)) }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement).provide(Has(cassandraZioSession))
        } yield ()
      }

      override def addPeriods(entries: List[Period]): LIO[Unit] = {
        val statement = quote { liftQuery(entries).foreach { entry => query[Period].insert(entry) } }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement).provide(Has(cassandraZioSession))
        } yield ()

      }

      override def updatePeriods(entries: List[Period]): LIO[Unit] = {
        val statement = quote {
          liftQuery(entries).foreach { entry =>
            query[Period].filter(p => p.id == entry.id && p.competitionId == entry.competitionId)
              .update(entry)
          }
        }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement).provide(Has(cassandraZioSession))
        } yield ()
      }

      override def removePeriod(competitionId: String)(id: String): LIO[Unit] = {
        val statement =
          quote { query[Period].filter(p => p.id == lift(id) && p.competitionId == lift(competitionId)).delete }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement).provide(Has(cassandraZioSession))
        } yield ()
      }

      override def removePeriods(competitionId: String): LIO[Unit] = {
        val statement = quote { query[Period].filter(p => p.competitionId == lift(competitionId)).delete }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement).provide(Has(cassandraZioSession))
        } yield ()
      }
    }
}
