package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.model.{Errors, Payload}
import compman.compsrv.model.command.Commands.{Command, DropScheduleCommand}
import compman.compsrv.model.events.{EventDTO, EventType}

object DropScheduleProc {
  def apply[F[+_]: CompetitionLogging.Service: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = { case x: DropScheduleCommand =>
    process(x.competitionId, state)
  }

  private def process[F[+_]: CompetitionLogging.Service: Monad: IdOperations: EventOperations](
    competitionId: Option[String],
    state: CompetitionState
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    import compman.compsrv.logic.logging._
    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] = for {
      _          <- EitherT.liftF(info(s"Dropping schedule"))
      event <- EitherT.liftF[F, Errors.Error, EventDTO](CommandEventOperations[F, EventDTO, EventType].create(
        `type` = EventType.SCHEDULE_DROPPED,
        competitorId = None,
        competitionId = competitionId,
        categoryId = None,
        payload = None
      ))
    } yield Seq(event)
    eventT.value
  }
}
