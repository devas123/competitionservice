package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.event.Events.{Event, FightResultSet}
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.model.CompetitorDisplayInfo
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, FightUpdateOperations}
import compman.compsrv.Utils

object FightResultSetProc {
  import cats.implicits._
  def apply[F[+_]: Monad: FightUpdateOperations: CompetitionQueryOperations]()
    : PartialFunction[Event[Any], F[Unit]] = { case x: FightResultSet => apply[F](x) }

  private def apply[F[+_]: Monad: FightUpdateOperations: CompetitionQueryOperations](event: FightResultSet): F[Unit] = {
    for {
      payload        <- OptionT.fromOption[F](event.payload)
      fightId        <- OptionT.fromOption[F](Option(payload.fightId))
      competitionId  <- OptionT.fromOption[F](event.competitionId)
      fightResultDto <- OptionT.fromOption[F](payload.fightResult)
      fightStatus <- OptionT.fromOption[F](Option(payload.status))
      scoresDto      <- OptionT.fromOption[F](Option(payload.scores))
      competitorIds = scoresDto.mapFilter(_.competitorId).filter(_ != null)
      competitors <- competitorIds.toList
        .traverse(id => OptionT(CompetitionQueryOperations[F].getCompetitorById(competitionId)(id)))
      competitorsMap = Utils.groupById(competitors)(_.id)
      scores = scoresDto.map(cs =>
        DtoMapping.mapCompScore(
          cs,
          competitorsMap.get(cs.getCompetitorId).map(c =>
            CompetitorDisplayInfo(c.id, Option(c.firstName), Option(c.lastName), c.academy.map(_.academyName))
          )
        )
      )
      fightResult = DtoMapping.mapFightResult(fightResultDto)
      _ <- OptionT.liftF(FightUpdateOperations[F].updateFightScoresAndResultAndStatus(competitionId)(fightId, scores.toList, fightResult, fightStatus))
    } yield ()
  }.value.map(_ => ())
}
