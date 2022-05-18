package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.model.command.Commands.{DropScheduleCommand, InternalCommandProcessorCommand}
import compman.compsrv.model.Errors
import compservice.model.protobuf.event.{Event, EventType}

object DropScheduleProc {
  def apply[F[+_]: CompetitionLogging.Service: Monad: IdOperations: EventOperations](): PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = { case x: DropScheduleCommand =>
    process(x.competitionId)
  }

  private def process[F[+_]: CompetitionLogging.Service: Monad: IdOperations: EventOperations](
    competitionId: Option[String]): F[Either[Errors.Error, Seq[Event]]] = {
    import compman.compsrv.logic.logging._
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      _          <- EitherT.liftF(info(s"Dropping schedule"))
      event <- EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event].create(
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
