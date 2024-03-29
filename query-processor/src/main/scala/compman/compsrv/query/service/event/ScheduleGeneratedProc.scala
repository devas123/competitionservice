package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.event.Events.{Event, ScheduleGeneratedEvent}
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.service.repository.CompetitionUpdateOperations

object ScheduleGeneratedProc {
  import cats.implicits._
  def apply[F[+_]: Monad: CompetitionUpdateOperations](): PartialFunction[Event[Any], F[Unit]] = {
    case x: ScheduleGeneratedEvent => apply[F](x)
  }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations](event: ScheduleGeneratedEvent): F[Unit] = {
    for {
      payload       <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      matsDto       <- OptionT.fromOption[F](Option(payload.getSchedule).map(_.mats))
      periodsDto    <- OptionT.fromOption[F](Option(payload.getSchedule).map(_.periods))
      mappedMats = matsDto.groupMap(_.periodId)(DtoMapping.mapMat)
      mappedPeriods = periodsDto
        .map(dto => DtoMapping.mapPeriod(competitionId)(dto)(mappedMats.getOrElse(dto.id, Seq.empty)))
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].addPeriods(mappedPeriods.toList))
    } yield ()
  }.value.map(_ => ())
}
