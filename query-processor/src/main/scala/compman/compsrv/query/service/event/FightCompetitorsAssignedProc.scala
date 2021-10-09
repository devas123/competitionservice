package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events.{Event, FightCompetitorsAssignedEvent}
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, CompetitionUpdateOperations}

object FightCompetitorsAssignedProc {
  import cats.implicits._
  def apply[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations, P <: Payload]()
    : PartialFunction[Event[P], F[Unit]] = { case x: FightCompetitorsAssignedEvent => apply[F](x) }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations](
    event: FightCompetitorsAssignedEvent
  ): F[Unit] = {
    for {
      payload       <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      assignments   <- OptionT.fromOption[F](Option(payload.getAssignments))
      fightIds = assignments.toList.flatMap(ass => (ass.getToFightId, ass.getFromFightId).toList).filter(_ != null)
        .toSet
      fightsList <- OptionT.liftF(CompetitionQueryOperations[F].getFightsByIds(competitionId)(fightIds))
      fights = fightsList.groupMapReduce(_.id)(identity)((a, _) => a)
      updates = assignments.toList.mapFilter(ass =>
        for {
          fromFight <- fights.get(ass.getFromFightId)
          toFight   <- fights.get(ass.getToFightId)
        } yield (ass, fromFight, toFight)
      ).mapFilter { case (ass, fromFight, toFight) =>
        for {
          scores <- Option(toFight.scores)
          score  <- scores.find(_.parentFightId.contains(fromFight.id))
          parentReferenceType <- score.parentReferenceType.orElse(Option(ass.getReferenceType))
          newScore  = score.copy(competitorId = Option(ass.getCompetitorId), parentReferenceType = Option(parentReferenceType))
          newScores = scores.filter(_.competitorId != newScore.competitorId) :+ newScore
        } yield toFight.copy(scores = newScores)
      }
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].updateFights(updates))
    } yield ()
  }.value.map(_ => ())
}
