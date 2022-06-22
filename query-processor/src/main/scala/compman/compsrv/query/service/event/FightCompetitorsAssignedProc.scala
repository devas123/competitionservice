package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.Utils
import compman.compsrv.model.event.Events.{Event, FightCompetitorsAssignedEvent}
import compman.compsrv.query.service.repository.{FightQueryOperations, FightUpdateOperations}

object FightCompetitorsAssignedProc {
  import cats.implicits._
  def apply[F[+_]: Monad: FightUpdateOperations: FightQueryOperations]()
    : PartialFunction[Event[Any], F[Unit]] = { case x: FightCompetitorsAssignedEvent => apply[F](x) }

  private def apply[F[+_]: Monad: FightUpdateOperations: FightQueryOperations](
    event: FightCompetitorsAssignedEvent
  ): F[Unit] = {
    for {
      payload       <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      assignments   <- OptionT.fromOption[F](Option(payload.assignments))
      value = assignments.toList.flatMap(ass => List(ass.toFightId, ass.fromFightId))
      fightIds = value
        .filter(f => f != null)
        .toSet
      fightsList <- OptionT.liftF(FightQueryOperations[F].getFightsByIds(competitionId)(fightIds))
      fights = Utils.groupById(fightsList)(_.id)
      updates = assignments.toList.mapFilter(ass =>
        for {
          fromFight <- fights.get(ass.fromFightId)
          toFight   <- fights.get(ass.toFightId)
        } yield (ass, fromFight, toFight)
      ).mapFilter { case (ass, fromFight, toFight) =>
        for {
          scores              <- Option(toFight.scores)
          compId <- Option(ass.competitorId)
          fromFightScore <- fromFight.scores.find(_.competitorId.contains(compId))
          score               <- scores.find(_.parentFightId.contains(fromFight.id))
          parentReferenceType <- score.parentReferenceType.orElse(Option(ass.referenceType))
          newScore = score
            .copy(competitorId = Option(compId),
              competitorFirstName = fromFightScore.competitorFirstName,
              competitorLastName = fromFightScore.competitorLastName,
              competitorAcademyName = fromFightScore.competitorAcademyName,
              parentReferenceType = Option(parentReferenceType))
          newScores = scores.filter(_.parentFightId != newScore.parentFightId) :+ newScore
        } yield toFight.copy(scores = newScores)
      }

      _ <-
        if (updates.nonEmpty) OptionT.liftF(FightUpdateOperations[F].updateFightScores(updates))
        else OptionT.liftF(Monad[F].unit)
    } yield ()
  }.value.map(_ => ())
}
