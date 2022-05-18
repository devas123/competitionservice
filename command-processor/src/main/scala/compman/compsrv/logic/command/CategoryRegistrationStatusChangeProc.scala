package compman.compsrv.logic.command

import cats.data.EitherT
import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.Errors
import compman.compsrv.model.Errors.{NoCategoryIdError, NoPayloadError}
import compman.compsrv.model.command.Commands.{CategoryRegistrationStatusChangeCommand, InternalCommandProcessorCommand}
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}

object CategoryRegistrationStatusChangeProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CompetitionState
  ): PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x @ CategoryRegistrationStatusChangeCommand(_, _, _) => process(x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    command: CategoryRegistrationStatusChangeCommand,
    state: CompetitionState
  ): F[Either[Errors.Error, Seq[Event]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      payload    <- EitherT.fromOption(command.payload, NoPayloadError())
      categoryId <- EitherT.fromOption(command.categoryId, NoCategoryIdError())
      exists = state.categories.exists(_.contains(categoryId))
      event <-
        if (!exists) {
          EitherT.fromEither(Left[Errors.Error, Event](Errors.CategoryDoesNotExist(
            command.categoryId.map(Seq(_)).getOrElse(Seq.empty)
          )))
        } else {
          EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event].create(
            `type` = EventType.CATEGORY_REGISTRATION_STATUS_CHANGED,
            competitorId = None,
            competitionId = command.competitionId,
            categoryId = command.categoryId,
            payload = Some(MessageInfo.Payload.CategoryRegistrationStatusChangePayload(payload))
          ))
        }
    } yield Seq(event)
    eventT.value
  }
}
