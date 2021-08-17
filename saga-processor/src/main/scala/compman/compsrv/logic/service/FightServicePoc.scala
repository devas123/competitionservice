package compman.compsrv.logic.service

import cats.Monad
import compman.compsrv.model.dto.brackets.FightReferenceType
import compman.compsrv.model.dto.competition.{FightDescriptionDTO, FightStatus}

object FightServicePoc {

  def advanceFighterToSiblingFights[F[_] : Monad](competitorId: String, sourceFight: String, referenceType: FightReferenceType, fights: Map[String, FightDescriptionDTO]): F[Map[String, FightDescriptionDTO]] = {
    Monad[F].tailRecM((competitorId, sourceFight, referenceType, fights))(tuple => {
      val (cid, sf, rt, fs) = tuple
      val eitherT = for {
        fight <- fs.get(sf)
        targetFightId = if (referenceType == FightReferenceType.LOSER) fight.getLoseFight else fight.getWinFight
        targetFight <- fs.get(targetFightId)
        updatedFights = fs + (targetFight.getId -> targetFight.setScores(targetFight.getScores.map(s => if (s.getParentFightId == sf && s.getParentReferenceType == rt) {
          s.setCompetitorId(cid)
        } else s)))
        res = if (targetFight.getStatus == FightStatus.UNCOMPLETABLE) {
          Left((cid, targetFight.getId, FightReferenceType.WINNER, updatedFights))
        } else {
          Right(updatedFights)
        }
      } yield res
      Monad[F].pure(eitherT.getOrElse(Right(fs)))
    })
  }
}
