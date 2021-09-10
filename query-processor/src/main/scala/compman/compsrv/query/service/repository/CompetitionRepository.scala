package compman.compsrv.query.service.repository

import compman.compsrv.model.dto.competition.CategoryStateDTO
import compman.compsrv.query.model.{CompetitionInfoTemplate, CompetitionProperties}

trait CompetitionRepository[F[_]] {
  def getCompetitionState(id: String): F[Option[CompetitionProperties]]
  def getCategories(competitionId: String): F[List[CategoryStateDTO]]
  def getCompetitionInfoTemplate(competitionId: String): F[Option[CompetitionInfoTemplate]]
}
