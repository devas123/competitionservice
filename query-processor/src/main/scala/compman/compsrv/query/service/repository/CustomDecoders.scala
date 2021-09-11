package compman.compsrv.query.service.repository

import compman.compsrv.model.dto.competition.CompetitionStatus
import io.getquill.MappedEncoding

trait CustomDecoders {
  implicit val competitionStatusDecoder: MappedEncoding[String, CompetitionStatus] =
    MappedEncoding[String, CompetitionStatus](CompetitionStatus.valueOf)
}
