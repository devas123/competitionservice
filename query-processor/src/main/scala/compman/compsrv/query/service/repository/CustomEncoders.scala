package compman.compsrv.query.service.repository

import compman.compsrv.model.dto.competition.CompetitionStatus
import io.getquill.MappedEncoding

trait CustomEncoders {
  implicit val competitionStatusEncoder: MappedEncoding[CompetitionStatus, String] =
    MappedEncoding[CompetitionStatus, String](_.name())
}
