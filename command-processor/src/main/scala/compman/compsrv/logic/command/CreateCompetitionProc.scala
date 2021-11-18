package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.model.{Errors, Payload}
import compman.compsrv.model.command.Commands.{Command, CreateCompetitionCommand}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.Errors.NoPayloadError
import compman.compsrv.model.events.payload.CompetitionCreatedPayload

object CreateCompetitionProc {
  def apply[F[+_]: CompetitionLogging.Service: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = {
    case x @ CreateCompetitionCommand(_, _, _) => process(x, state)
  }

  private def process[F[+_]: CompetitionLogging.Service: Monad: IdOperations: EventOperations](
    command: CreateCompetitionCommand,
    state: CompetitionState
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    import compman.compsrv.logic.logging._
    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] = for {
      payload <- EitherT.fromOption(command.payload, NoPayloadError())
      _       <- EitherT.liftF(info(s"Creating competition: ${payload.getProperties}"))
      event <- EitherT.liftF[F, Errors.Error, EventDTO](CommandEventOperations[F, EventDTO, EventType].create(
        `type` = EventType.COMPETITION_CREATED,
        competitorId = None,
        competitionId = command.competitionId,
        categoryId = command.categoryId,
        payload = Some(
          new CompetitionCreatedPayload().setReginfo(Option(payload.getReginfo).orElse(state.registrationInfo).orNull)
            .setProperties(Option(payload.getProperties).orElse(state.competitionProperties).orNull)
        )
      ))
    } yield Seq(event)
    eventT.value
  }
}
