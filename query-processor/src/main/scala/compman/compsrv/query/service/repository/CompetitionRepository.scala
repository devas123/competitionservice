package compman.compsrv.query.service.repository

import compman.compsrv.model.dto.competition.CategoryStateDTO
import compman.compsrv.query.model._
import compman.compsrv.query.service.repository.CompetitionRepository.CompetitionRepositoryService
import zio.{Has, URIO, ZIO}

object CompetitionRepository {

  type CompetitionRepositoryService = Has[CompetitionRepository]

  def getCompetitionState(id: String): URIO[CompetitionRepositoryService, Option[CompetitionProperties]] = ZIO
    .accessM {
      _.get[CompetitionRepository].getCompetitionState(id)
    }

  def getCategories(competitionId: String): URIO[CompetitionRepositoryService, List[CategoryStateDTO]] = ZIO
    .accessM {
      _.get[CompetitionRepository].getCategories(competitionId)
    }

  def getCompetitionInfoTemplate(
                                  competitionId: String
                                ): URIO[CompetitionRepositoryService, Option[CompetitionInfoTemplate]] = ZIO
    .accessM {
      _.get[CompetitionRepository].getCompetitionInfoTemplate(competitionId)
    }
}

trait CompetitionQueryOperations[F[+_]] {
  def getCompetitionProperties(id: String): F[Option[CompetitionProperties]]

  def getCategoriesByCompetitionId(competitionId: String): F[List[Category]]

  def getCompetitionInfoTemplate(
                                  competitionId: String
                                ): F[Option[CompetitionInfoTemplate]]

  def getCategoryById(competitionId: String)(id: String): F[Option[Category]]

  def searchCategory(competitionId: String)(searchString: String, pagination: Option[Pagination]): F[(List[Category], Pagination)]

  def getFightsByMat(competitionId: String)(matId: String): F[List[Fight]]

  def getFightsByStage(competitionId: String)(stageId: String): F[List[Fight]]

  def getFightById(competitionId: String)(id: String): F[Option[Fight]]

  def getCompetitorById(competitionId: String)(id: String): F[Option[Competitor]]

  def getCompetitorsByCategoryId(competitionId: String)(categoryId: String, pagination: Option[Pagination], searchString: Option[String] = None): F[(List[Competitor], Pagination)]

  def getCompetitorsByCompetitionId(competitionId: String)(pagination: Option[Pagination], searchString: Option[String] = None): F[(List[Competitor], Pagination)]

  def getCompetitorsByAcademyId(competitionId: String)(academyId: String, pagination: Option[Pagination], searchString: Option[String] = None): F[(List[Competitor], Pagination)]

  def getRegistrationGroups(competitionId: String): F[List[RegistrationGroup]]

  def getRegistrationGroupById(competitionId: String)(id: String): F[Option[RegistrationGroup]]

  def getRegistrationPeriods(competitionId: String): F[List[RegistrationPeriod]]

  def getRegistrationPeriodById(competitionId: String)(id: String): F[Option[RegistrationPeriod]]

  def getScheduleEntriesByPeriodId(competitionId: String)(periodId: String): F[List[ScheduleEntry]]

  def getScheduleRequirementsByPeriodId(competitionId: String)(periodId: String): F[List[ScheduleRequirement]]

  def getPeriodsByCompetitionId(competitionId: String): F[List[Period]]

  def getPeriodById(competitionId: String)(id: String): F[Option[Period]]

  def getStagesByCategory(competitionId: String)(categoryId: String): F[List[StageDescriptor]]
  def getStageById(competitionId: String)(id: String): F[Option[StageDescriptor]]
}

object CompetitionQueryOperations {
  def apply[F[+_]](implicit F: CompetitionQueryOperations[F]): CompetitionQueryOperations[F] = F

  def getCompetitionProperties[F[+_] : CompetitionQueryOperations](id: String): F[Option[CompetitionProperties]] = CompetitionQueryOperations[F].getCompetitionProperties(id)

  def getCategoriesByCompetitionId[F[+_] : CompetitionQueryOperations](competitionId: String): F[List[Category]] = CompetitionQueryOperations[F].getCategoriesByCompetitionId(competitionId)

  def getCompetitionInfoTemplate[F[+_] : CompetitionQueryOperations](
                                                                      competitionId: String
                                                                    ): F[Option[CompetitionInfoTemplate]] = CompetitionQueryOperations[F].getCompetitionInfoTemplate(competitionId)

  def getCategoryById[F[+_] : CompetitionQueryOperations](competitionId: String)(id: String): F[Option[Category]] = CompetitionQueryOperations[F].getCategoryById(competitionId)(id)

  def searchCategory[F[+_] : CompetitionQueryOperations](competitionId: String)(searchString: String, pagination: Option[Pagination]): F[(List[Category], Pagination)] = CompetitionQueryOperations[F].searchCategory(competitionId)(searchString, pagination)

  def getFightsByMat[F[+_] : CompetitionQueryOperations](competitionId: String)(matId: String): F[List[Fight]] = CompetitionQueryOperations[F].getFightsByMat(competitionId)(matId)

  def getFightsByStage[F[+_] : CompetitionQueryOperations](competitionId: String)(stageId: String): F[List[Fight]] = CompetitionQueryOperations[F].getFightsByStage(competitionId)(stageId)

  def getFightById[F[+_] : CompetitionQueryOperations](competitionId: String)(id: String): F[Option[Fight]] = CompetitionQueryOperations[F].getFightById(competitionId)(id)

  def getCompetitorById[F[+_] : CompetitionQueryOperations](competitionId: String)(id: String): F[Option[Competitor]] = CompetitionQueryOperations[F].getCompetitorById(competitionId)(id)

  def getCompetitorsByCategoryId[F[+_] : CompetitionQueryOperations](competitionId: String)(categoryId: String, pagination: Option[Pagination], searchString: Option[String] = None): F[(List[Competitor], Pagination)] = CompetitionQueryOperations[F].getCompetitorsByCategoryId(competitionId)(categoryId, pagination, searchString)

  def getCompetitorsByCompetitionId[F[+_] : CompetitionQueryOperations](competitionId: String)(pagination: Option[Pagination], searchString: Option[String] = None): F[(List[Competitor], Pagination)] = CompetitionQueryOperations[F].getCompetitorsByCompetitionId(competitionId)(pagination, searchString)

  def getCompetitorsByAcademyId[F[+_] : CompetitionQueryOperations](competitionId: String)(academyId: String, pagination: Option[Pagination], searchString: Option[String] = None): F[(List[Competitor], Pagination)] = CompetitionQueryOperations[F].getCompetitorsByAcademyId(competitionId)(academyId, pagination, searchString)

  def getRegistrationGroups[F[+_] : CompetitionQueryOperations](competitionId: String): F[List[RegistrationGroup]] = CompetitionQueryOperations[F].getRegistrationGroups(competitionId)

  def getRegistrationGroupById[F[+_] : CompetitionQueryOperations](competitionId: String)(id: String): F[Option[RegistrationGroup]] = CompetitionQueryOperations[F].getRegistrationGroupById(competitionId)(id)

  def getRegistrationPeriods[F[+_] : CompetitionQueryOperations](competitionId: String): F[List[RegistrationPeriod]] = CompetitionQueryOperations[F].getRegistrationPeriods(competitionId)

  def getRegistrationPeriodById[F[+_] : CompetitionQueryOperations](competitionId: String)(id: String): F[Option[RegistrationPeriod]] = CompetitionQueryOperations[F].getRegistrationPeriodById(competitionId)(id)

  def getScheduleEntriesByPeriodId[F[+_] : CompetitionQueryOperations](competitionId: String)(periodId: String): F[List[ScheduleEntry]] = CompetitionQueryOperations[F].getScheduleEntriesByPeriodId(competitionId)(periodId)

  def getScheduleRequirementsByPeriodId[F[+_] : CompetitionQueryOperations](competitionId: String)(periodId: String): F[List[ScheduleRequirement]] = CompetitionQueryOperations[F].getScheduleRequirementsByPeriodId(competitionId)(periodId)

  def getPeriodsByCompetitionId[F[+_] : CompetitionQueryOperations](competitionId: String): F[List[Period]] = CompetitionQueryOperations[F].getPeriodsByCompetitionId(competitionId)

  def getPeriodById[F[+_] : CompetitionQueryOperations](competitionId: String)(id: String): F[Option[Period]] = CompetitionQueryOperations[F].getPeriodById(competitionId)(id)

}

private[repository] trait CompetitionRepository {
  def getCompetitionState(id: String): URIO[CompetitionRepositoryService, Option[CompetitionProperties]]

  def getCategories(competitionId: String): URIO[CompetitionRepositoryService, List[CategoryStateDTO]]

  def getCompetitionInfoTemplate(
                                  competitionId: String
                                ): URIO[CompetitionRepositoryService, Option[CompetitionInfoTemplate]]

}
