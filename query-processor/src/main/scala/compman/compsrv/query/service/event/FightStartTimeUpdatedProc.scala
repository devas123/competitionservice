package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events.{Event, FightStartTimeUpdatedEvent}
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, CompetitionUpdateOperations}

object FightStartTimeUpdatedProc {
  import cats.implicits._
  def apply[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations, P <: Payload]()
    : PartialFunction[Event[P], F[Unit]] = { case x: FightStartTimeUpdatedEvent => apply[F](x) }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations](
    event: FightStartTimeUpdatedEvent
  ): F[Unit] = {
    for {
      payload       <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      updates       <- OptionT.fromOption[F](Option(payload.getNewFights))
      existing <- updates.groupBy(_.getFightCategoryId).toList.traverse(arr =>  OptionT
        .liftF(CompetitionQueryOperations[F].getFightsByIds(competitionId)(arr._1, arr._2.map(_.getFightId).toSet)))
      updatesMap = updates.groupMapReduce(_.getFightId)(identity)((a, _) => a)
      periods       <- OptionT.liftF(CompetitionQueryOperations[F].getPeriodsByCompetitionId(competitionId))
      mats         = periods.flatMap(_.mats).groupMapReduce(_.matId)(identity)((a, _) => a)
      existingUpdated = existing.flatten.map { f =>
        val u = updatesMap(f.id)
        val schedule = f.scheduleInfo.map(s => s.copy(
          mat = Option(u.getMatId).flatMap(mats.get).getOrElse(s.mat),
          numberOnMat = Option(u.getNumberOnMat),
          periodId = Option(u.getPeriodId),
          startTime = Option(u.getStartTime),
          invalid = Option(u.getInvalid)
        ))
        f.copy(scheduleInfo = schedule)
      }
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].updateFightScores(existingUpdated))
    } yield ()
  }.value.map(_ => ())
}
