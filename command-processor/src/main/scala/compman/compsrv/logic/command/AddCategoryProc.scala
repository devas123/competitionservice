package compman.compsrv.logic.command

import cats.data.EitherT
import cats.Monad
import compservice.model.protobuf.model.CommandProcessorCompetitionState
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.command.Commands.{AddCategory, InternalCommandProcessorCommand}
import compman.compsrv.model.Errors
import compman.compsrv.model.Errors.{CategoryAlreadyExists, InternalError}
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload.CategoryAddedPayload

object AddCategoryProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x @ AddCategory(_, _) => addCategory(x, state)
  }

  private def addCategory[F[+_]: Monad: IdOperations: EventOperations](
    command: AddCategory,
    state: CommandProcessorCompetitionState
  ): F[Either[Errors.Error, Seq[Event]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      payload <- EitherT.fromOption(command.payload, InternalError())
      id      <- EitherT.liftF[F, Errors.Error, String](IdOperations[F].categoryId(payload.getCategory))
      exists = state.categories.contains(id)
      event <-
        if (exists) {
          EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event].create(
            `type` = EventType.CATEGORY_ADDED,
            competitorId = None,
            competitionId = command.competitionId,
            categoryId = Some(id),
            payload = Some(MessageInfo.Payload.CategoryAddedPayload(CategoryAddedPayload(payload.category)))
          ))
        } else { EitherT(CommandEventOperations[F, Event].error(CategoryAlreadyExists(id, payload.getCategory))) }
    } yield Seq(event)
    eventT.value
  }
}
