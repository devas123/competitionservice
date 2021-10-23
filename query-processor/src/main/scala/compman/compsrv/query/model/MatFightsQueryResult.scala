package compman.compsrv.query.model

import compman.compsrv.model.dto.competition.{CompetitorDTO, FightDescriptionDTO}
import compman.compsrv.model.dto.dashboard.MatStateDTO

case class MatFightsQueryResult (
  competitors: List[CompetitorDTO],
  fights: List[FightDescriptionDTO]
                                )

case class MatsQueryResult (
                                  competitors: List[CompetitorDTO],
                                  mats: List[MatStateDTO]
                                )
