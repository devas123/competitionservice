package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.Utils
import compman.compsrv.model.event.Events.{Event, FightStartTimeUpdatedEvent}
import compman.compsrv.query.model.FightStartTimeUpdate
import compman.compsrv.query.model.mapping.DtoMapping.toDate
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, FightQueryOperations, FightUpdateOperations}

object FightStartTimeUpdatedProc {
  import cats.implicits._
  def apply[F[+_]: Monad: FightUpdateOperations: CompetitionQueryOperations: FightQueryOperations]()
    : PartialFunction[Event[Any], F[Unit]] = { case x: FightStartTimeUpdatedEvent => apply[F](x) }

  private def apply[F[+_]: Monad: FightUpdateOperations: CompetitionQueryOperations: FightQueryOperations](
    event: FightStartTimeUpdatedEvent
  ): F[Unit] = {
    for {
      payload       <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      updates       <- OptionT.fromOption[F](Option(payload.newFights))
      updatesMap = Utils.groupById(updates)(_.fightId)
      periods <- OptionT.liftF(CompetitionQueryOperations[F].getPeriodsByCompetitionId(competitionId))
      mats = Utils.groupById(periods.flatMap(_.mats))(_.matId)
      existingUpdated = updatesMap.map { case (id, f) =>
        val u   = updatesMap(f.fightId)
        val mat = Option(u.matId).flatMap(mats.get)
        FightStartTimeUpdate(
          id = id,
          scheduleEntryId = Option(u.scheduleEntryId),
          matId = mat.map(_.matId),
          matName = mat.map(_.name),
          matOrder = mat.map(_.matOrder),
          numberOnMat = Option(u.numberOnMat),
          periodId = Option(u.periodId),
          startTime = u.startTime.map(toDate),
          invalid = Option(u.invalid),
          competitionId = competitionId,
          categoryId = f.fightCategoryId,
          priority = None
        )
      }.toList
      _ <- OptionT.liftF(FightUpdateOperations[F].updateFightStartTime(existingUpdated))
    } yield ()
  }.value.map(_ => ())
}
