package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.Utils
import compman.compsrv.model.event.Events.{Event, FightCompetitorsAssignedEvent}
import compman.compsrv.query.service.repository.{
  CompetitionQueryOperations,
  FightQueryOperations,
  FightUpdateOperations
}

object FightCompetitorsAssignedProc {
  import cats.implicits._
  def apply[F[+_]: Monad: FightUpdateOperations: FightQueryOperations: CompetitionQueryOperations](): PartialFunction[Event[Any], F[Unit]] = {
    case x: FightCompetitorsAssignedEvent => apply[F](x)
  }

  private def apply[F[+_]: Monad: FightUpdateOperations: FightQueryOperations: CompetitionQueryOperations](
    event: FightCompetitorsAssignedEvent
  ): F[Unit] = {
    for {
      payload       <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      categoryId    <- OptionT.fromOption[F](event.categoryId)
      assignments   <- OptionT.fromOption[F](Option(payload.assignments))
      fightIds = assignments.toList.flatMap(ass => List(ass.toFightId, ass.fromFightId))
        .filter(f => f != null && f.nonEmpty).toSet
      fightsList <- OptionT.liftF(FightQueryOperations[F].getFightsByIds(competitionId)(fightIds))
      competitorsWithPagination <- OptionT
        .liftF(CompetitionQueryOperations[F].getCompetitorsByCategoryId(competitionId)(categoryId, None))
      fights = Utils.groupById(fightsList)(_.id)
      competitors = Utils.groupById(competitorsWithPagination._1)(_.id)
      updates = assignments.toList.mapFilter(ass =>
        for {
          fromFight <- fights.get(ass.fromFightId)
          toFight   <- fights.get(ass.toFightId)
        } yield (ass, fromFight, toFight)
      ).mapFilter { case (ass, fromFight, toFight) =>
        for {
          scores <- Option(toFight.scores)
          compId = ass.competitorId
          competitor <- competitors.get(compId)
          score               <- scores.find(_.parentFightId.contains(fromFight.id))
          parentReferenceType <- score.parentReferenceType.orElse(Option(ass.referenceType))
          newScore = score.copy(
            competitorId = Option(compId),
            competitorFirstName = Some(competitor.firstName),
            competitorLastName = Some(competitor.lastName),
            competitorAcademyName = competitor.academy.map(_.academyName),
            parentReferenceType = Option(parentReferenceType)
          )
          newScores = scores.filter(_.parentFightId != newScore.parentFightId) :+ newScore
        } yield toFight.copy(scores = newScores)
      }
      _ <- OptionT.liftF(FightUpdateOperations[F].updateFightScores(updates).whenA(updates.nonEmpty))
    } yield ()
  }.value.map(_ => ())
}
