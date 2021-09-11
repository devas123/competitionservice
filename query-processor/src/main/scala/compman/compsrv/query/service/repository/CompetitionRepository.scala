package compman.compsrv.query.service.repository

import compman.compsrv.model.dto.competition.CategoryStateDTO
import compman.compsrv.query.model.{CompetitionInfoTemplate, CompetitionProperties}
import compman.compsrv.query.service.repository.CompetitionRepository.CompetitionRepositoryService
import zio.{Has, URIO, ZIO}

object CompetitionRepository {

  type CompetitionRepositoryService = Has[CompetitionRepository]

  def getCompetitionState(id: String): URIO[CompetitionRepositoryService, Option[CompetitionProperties]] = ZIO
    .accessM { _.get[CompetitionRepository].getCompetitionState(id) }
  def getCategories(competitionId: String): URIO[CompetitionRepositoryService, List[CategoryStateDTO]] = ZIO
    .accessM { _.get[CompetitionRepository].getCategories(competitionId) }

  def getCompetitionInfoTemplate(
    competitionId: String
  ): URIO[CompetitionRepositoryService, Option[CompetitionInfoTemplate]] = ZIO
    .accessM { _.get[CompetitionRepository].getCompetitionInfoTemplate(competitionId) }
}

trait CompetitionRepository {
  def getCompetitionState(id: String): URIO[CompetitionRepositoryService, Option[CompetitionProperties]]
  def getCategories(competitionId: String): URIO[CompetitionRepositoryService, List[CategoryStateDTO]]
  def getCompetitionInfoTemplate(
    competitionId: String
  ): URIO[CompetitionRepositoryService, Option[CompetitionInfoTemplate]]

}
