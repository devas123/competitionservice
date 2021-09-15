package compman.compsrv.query.service.repository

import compman.compsrv.model.dto.brackets.StageStatus
import compman.compsrv.query.model._

trait CompetitionUpdateOperations[F[+_]] {
  def updateRegistrationOpen(competitionId: String): F[Unit]

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
}
