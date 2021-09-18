package compman.compsrv.query.service.repository

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.model.dto.brackets.StageStatus
import compman.compsrv.query.model._
import compman.compsrv.query.model.CompetitionProperties.CompetitionInfoTemplate
import io.getquill._
import io.getquill.context.cassandra.encoding.{Decoders, Encoders}

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
  def addFights(fights: Seq[Fight]): F[Unit]
  def updateFight(fight: Fight): F[Unit]
  def updateFights(fights: Seq[Fight]): F[Unit]
  def removeFight(competitionId: String)(id: String): F[Unit]
  def removeFights(competitionId: String)(ids: Seq[String]): F[Unit]
  def addRegistrationGroup(group: RegistrationGroup): F[Unit]
  def addRegistrationGroups(groups: Seq[RegistrationGroup]): F[Unit]
  def updateRegistrationGroup(group: RegistrationGroup): F[Unit]
  def updateRegistrationGroups(groups: Seq[RegistrationGroup]): F[Unit]
  def removeRegistrationGroup(competitionId: String)(id: String): F[Unit]
  def addRegistrationPeriod(period: RegistrationPeriod): F[Unit]
  def updateRegistrationPeriod(period: RegistrationPeriod): F[Unit]
  def updateRegistrationPeriods(periods: Seq[RegistrationPeriod]): F[Unit]
  def removeRegistrationPeriod(competitionId: String)(id: String): F[Unit]
  def addScheduleEntry(entry: ScheduleEntry): F[Unit]
  def addScheduleEntries(entries: Seq[ScheduleEntry]): F[Unit]
  def removeScheduleEntry(competitionId: String)(id: String): F[Unit]
  def removeScheduleEntries(competitionId: String): F[Unit]
  def addScheduleRequirement(entry: ScheduleRequirement): F[Unit]
  def removeScheduleRequirement(competitionId: String)(id: String): F[Unit]
  def removeScheduleRequirements(competitionId: String): F[Unit]
  def addPeriod(entry: Period): F[Unit]
  def addPeriods(entries: Seq[Period]): F[Unit]
  def updatePeriods(entries: Seq[Period]): F[Unit]
  def removePeriod(competitionId: String)(id: String): F[Unit]
  def removePeriods(competitionId: String): F[Unit]
}

object CompetitionUpdateOperations {
  def apply[F[+_]](implicit F: CompetitionUpdateOperations[F]): CompetitionUpdateOperations[F] = F

  def live(implicit log: CompetitionLogging.Service[LIO]): CompetitionUpdateOperations[RepoIO] =
    new CompetitionUpdateOperations[RepoIO] {
      private lazy val ctx =
        new CassandraZioContext(SnakeCase) with CustomDecoders with CustomEncoders with Encoders with Decoders
      import ctx._

      override def updateRegistrationOpen(competitionId: String)(isOpen: Boolean): RepoIO[Unit] = {
        val statement = quote {
          query[CompetitionProperties].filter(_.id == lift(competitionId)).update(_.registrationOpen -> lift(isOpen))
        }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement)
        } yield ()
      }

      override def addCompetitionProperties(competitionProperties: CompetitionProperties): RepoIO[Unit] = {
        val statement = quote { query[CompetitionProperties].insert(liftCaseClass(competitionProperties)) }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement)
        } yield ()
      }

      override def updateCompetitionProperties(competitionProperties: CompetitionProperties): RepoIO[Unit] = {
        val statement = quote {
          query[CompetitionProperties].filter(_.id == lift(competitionProperties.id))
            .update(liftCaseClass(competitionProperties))
        }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement)
        } yield ()
      }

      override def removeCompetitionProperties(id: String): RepoIO[Unit] = {
        val remove = quote { query[CompetitionProperties].filter(_.id == lift(id)).delete }
        for {
          _ <- log.info(remove.toString)
          _ <- run(remove)
        } yield ()
      }

      override def addCompetitionInfoTemplate(
        competitionId: String
      )(competitionInfoTemplate: CompetitionInfoTemplate): RepoIO[Unit] = {
        val statement = quote {
          query[CompetitionProperties].filter(_.id == lift(competitionId))
            .update(_.infoTemplate.template -> lift(competitionInfoTemplate.template))
        }
        for {
          _ <- log.info(statement.toString)
          _ <- run(statement)
        } yield ()
      }

      override def removeCompetitionInfoTemplate(competitionId: String): RepoIO[Unit] =
        addCompetitionInfoTemplate(competitionId)(CompetitionInfoTemplate(Array.empty))

      override def addStage(stageDescriptor: StageDescriptor): RepoIO[Unit] = {
//        val statement = quote { query[StageDescriptor].insert(liftCaseClass(stageDescriptor)) }
//        for {
//          _ <- log.info(statement.toString)
//          _ <- run(statement)
//        } yield ()
        ???
      }

      override def updateStage(stageDescriptor: StageDescriptor): RepoIO[Unit] = ???

      override def updateStageStatus(competitionId: String)(stageId: String, newStatus: StageStatus): RepoIO[Unit] = ???

      override def addCategory(category: Category): RepoIO[Unit] = ???

      override def updateCategoryRegistrationStatus(
        competitionId: String
      )(id: String, newStatus: Boolean): RepoIO[Unit] = ???

      override def removeCategory(competitionId: String)(id: String): RepoIO[Unit] = ???

      override def addCompetitor(competitor: Competitor): RepoIO[Unit] = ???

      override def updateCompetitor(competitor: Competitor): RepoIO[Unit] = ???

      override def removeCompetitor(competitionId: String)(id: String): RepoIO[Unit] = ???

      override def addFight(fight: Fight): RepoIO[Unit] = ???

      override def addFights(fights: Seq[Fight]): RepoIO[Unit] = ???

      override def updateFight(fight: Fight): RepoIO[Unit] = ???

      override def updateFights(fights: Seq[Fight]): RepoIO[Unit] = ???

      override def removeFight(competitionId: String)(id: String): RepoIO[Unit] = ???

      override def removeFights(competitionId: String)(ids: Seq[String]): RepoIO[Unit] = ???

      override def addRegistrationGroup(group: RegistrationGroup): RepoIO[Unit] = ???

      override def addRegistrationGroups(groups: Seq[RegistrationGroup]): RepoIO[Unit] = ???

      override def updateRegistrationGroup(group: RegistrationGroup): RepoIO[Unit] = ???

      override def updateRegistrationGroups(groups: Seq[RegistrationGroup]): RepoIO[Unit] = ???

      override def removeRegistrationGroup(competitionId: String)(id: String): RepoIO[Unit] = ???

      override def addRegistrationPeriod(period: RegistrationPeriod): RepoIO[Unit] = ???

      override def updateRegistrationPeriod(period: RegistrationPeriod): RepoIO[Unit] = ???

      override def updateRegistrationPeriods(periods: Seq[RegistrationPeriod]): RepoIO[Unit] = ???

      override def removeRegistrationPeriod(competitionId: String)(id: String): RepoIO[Unit] = ???

      override def addScheduleEntry(entry: ScheduleEntry): RepoIO[Unit] = ???

      override def addScheduleEntries(entries: Seq[ScheduleEntry]): RepoIO[Unit] = ???

      override def removeScheduleEntry(competitionId: String)(id: String): RepoIO[Unit] = ???

      override def removeScheduleEntries(competitionId: String): RepoIO[Unit] = ???

      override def addScheduleRequirement(entry: ScheduleRequirement): RepoIO[Unit] = ???

      override def removeScheduleRequirement(competitionId: String)(id: String): RepoIO[Unit] = ???

      override def removeScheduleRequirements(competitionId: String): RepoIO[Unit] = ???

      override def addPeriod(entry: Period): RepoIO[Unit] = ???

      override def addPeriods(entries: Seq[Period]): RepoIO[Unit] = ???

      override def updatePeriods(entries: Seq[Period]): RepoIO[Unit] = ???

      override def removePeriod(competitionId: String)(id: String): RepoIO[Unit] = ???

      override def removePeriods(competitionId: String): RepoIO[Unit] = ???
    }
}
