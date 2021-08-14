package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.{CompetitionState, Errors, Payload}
import compman.compsrv.model.command.Commands.{CategoryRegistrationStatusChangeCommand, Command}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.Errors.NoPayloadError

object DeleteRegistrationGroupProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
      state: CompetitionState
  ): PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = {
    case x @ CategoryRegistrationStatusChangeCommand(_, _, _) =>
      process(x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
      command: CategoryRegistrationStatusChangeCommand,
      state: CompetitionState
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] =
      for {
        payload <- EitherT.fromOption(command.payload, NoPayloadError())
        exists = state
          .categories
          .exists(_.exists(cat => command.categoryId.forall(cid => cid == cat.getId)))
        event <-
          if (!exists) {
            EitherT.fromEither(
              Left[Errors.Error, EventDTO](
                Errors.CategoryDoesNotExist(command.categoryId.map(Array(_)).getOrElse(Array.empty))
              )
            )
          } else {
            EitherT.liftF[F, Errors.Error, EventDTO](
              CommandEventOperations[F, EventDTO, EventType].create(
                `type` = EventType.CATEGORY_REGISTRATION_STATUS_CHANGED,
                competitorId = None,
                competitionId = command.competitionId,
                categoryId = command.categoryId,
                payload = Some(payload)
              )
            )
          }
      } yield Seq(event)
    eventT.value
  }
}
