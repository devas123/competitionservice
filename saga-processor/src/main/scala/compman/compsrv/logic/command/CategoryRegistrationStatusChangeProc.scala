package compman.compsrv.logic.command

import cats.data.EitherT
import cats.Monad
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.{CompetitionState, Errors, Payload}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.Errors.{NoCategoryIdError, NoPayloadError}
import compman.compsrv.model.command.Commands.{CategoryRegistrationStatusChangeCommand, Command}

object CategoryRegistrationStatusChangeProc {
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
        payload    <- EitherT.fromOption(command.payload, NoPayloadError())
        categoryId <- EitherT.fromOption(command.categoryId, NoCategoryIdError())
        exists = state.categories.exists(_.contains(categoryId))
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
