package compman.compsrv.model

import compman.compsrv.model.dto.competition.CompetitorDTO

object Errors {
  sealed trait Error
  final case class GeneralError() extends Error
  final case class CompetitorAlreadyExists(id: String, competitor: CompetitorDTO) extends Error
}
