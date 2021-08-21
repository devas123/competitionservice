package compman.compsrv.model

import compman.compsrv.model.dto.brackets.StageRoundType
import compman.compsrv.model.dto.competition.{CompScoreDTO, FightDescriptionDTO, ScoreDTO}

package object extension {

  def createEmptyScore: ScoreDTO = new ScoreDTO()
    .setAdvantages(0)
    .setPenalties(0)
    .setPoints(0)
    .setPointGroups(Array.empty)

  implicit class FightDescrOps(f: FightDescriptionDTO) {
    def copy(
        fightName: String = f.getFightName,
        roundType: StageRoundType = f.getRoundType,
        winFight: String = f.getWinFight,
        loseFight: String = f.getLoseFight,
        scores: Array[CompScoreDTO] = f.getScores
    ): FightDescriptionDTO = f
      .setWinFight(winFight)
      .setScores(scores)
      .setLoseFight(loseFight)
      .setRoundType(roundType)
      .setFightName(fightName)

    def round: Option[Int]                = Option(f.getRound).map(_.toInt)
    def roundOrZero: Int                  = Option(f.getRound).map(_.toInt).getOrElse(0)
    def roundType: Option[StageRoundType] = Option(f.getRoundType)

    def containsFighter(cid: String): Boolean = scores.exists(_.exists(_.getCompetitorId == cid))

    def winnerId: Option[String] = Option(f.getFightResult).flatMap(res => Option(res.getWinnerId))

    def loserId: Option[String] =
      for {
        res        <- Option(f.getFightResult)
        scores     <- Option(f.getScores)
        loserScore <- scores.find(_.getCompetitorId != res.getWinnerId)
        id = loserScore.getCompetitorId
      } yield id

    def scores: Option[Array[CompScoreDTO]] = Option(f.getScores)

    def pushCompetitor(competitorId: String): Option[FightDescriptionDTO] = {
      for {
        sc <- scores
        ind = sc.indexWhere(_.getCompetitorId == null)
        res = copy(scores =
          (sc.slice(0, ind) :+ sc(ind).setCompetitorId(competitorId)) ++
            sc.slice(ind + 1, sc.length)
        )
      } yield res
    }
  }
}
