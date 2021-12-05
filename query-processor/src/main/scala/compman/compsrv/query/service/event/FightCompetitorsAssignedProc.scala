package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.Utils
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events.{Event, FightCompetitorsAssignedEvent}
import compman.compsrv.query.service.repository.{FightQueryOperations, FightUpdateOperations}

object FightCompetitorsAssignedProc {
  import cats.implicits._
  def apply[F[+_]: Monad: FightUpdateOperations: FightQueryOperations, P <: Payload]()
    : PartialFunction[Event[P], F[Unit]] = { case x: FightCompetitorsAssignedEvent => apply[F](x) }

  private def apply[F[+_]: Monad: FightUpdateOperations: FightQueryOperations](
    event: FightCompetitorsAssignedEvent
  ): F[Unit] = {
    for {
      payload       <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      categoryId    <- OptionT.fromOption[F](event.categoryId)
      assignments   <- OptionT.fromOption[F](Option(payload.getAssignments))
      value = assignments.toList.flatMap(ass => List(ass.getToFightId, ass.getFromFightId))
      fightIds = value
        .filter(f => f != null)
        .toSet
      fightsList <- OptionT.liftF(FightQueryOperations[F].getFightsByIds(competitionId)(categoryId, fightIds))
      fights = Utils.groupById(fightsList)(_.id)
      updates = assignments.toList.mapFilter(ass =>
        for {
          fromFight <- fights.get(ass.getFromFightId)
          toFight   <- fights.get(ass.getToFightId)
        } yield (ass, fromFight, toFight)
      ).mapFilter { case (ass, fromFight, toFight) =>
        for {
          scores              <- Option(toFight.scores)
          score               <- scores.find(_.parentFightId.contains(fromFight.id))
          parentReferenceType <- score.parentReferenceType.orElse(Option(ass.getReferenceType))
          newScore = score
            .copy(competitorId = Option(ass.getCompetitorId), parentReferenceType = Option(parentReferenceType))
          newScores = scores.filter(_.parentFightId != newScore.parentFightId) :+ newScore
        } yield toFight.copy(scores = newScores)
      }

      _ <-
        if (updates.nonEmpty) OptionT.liftF(FightUpdateOperations[F].updateFightScores(updates))
        else OptionT.liftF(Monad[F].unit)
    } yield ()
  }.value.map(_ => ())
}
