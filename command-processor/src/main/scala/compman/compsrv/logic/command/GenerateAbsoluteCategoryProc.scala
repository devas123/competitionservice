package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import cats.implicits._
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.Errors.{CategoryAlreadyExists, InternalError}
import compman.compsrv.model.command.Commands.{GenerateAbsoluteCategoryCommand, InternalCommandProcessorCommand}
import compman.compsrv.model.Errors
import compservice.model.protobuf.commandpayload.CompetitorCategoryAddedPayload
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload.CategoryAddedPayload

object GenerateAbsoluteCategoryProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CompetitionState
  ): PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x: GenerateAbsoluteCategoryCommand => addCategory[F](x, state)
  }

  private def addCategory[F[+_]: Monad: IdOperations: EventOperations](
    command: GenerateAbsoluteCategoryCommand,
    state: CompetitionState
  ): F[Either[Errors.Error, Seq[Event]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      payload <- EitherT.fromOption[F](command.payload, InternalError())
      id      <- EitherT.liftF[F, Errors.Error, String](IdOperations[F].categoryId(payload.getCategory))
      exists  <- EitherT.fromOption[F](state.categories.map(_.contains(id)), Errors.InternalError())
      event <-
        if (exists) {
          EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event].create(
            `type` = EventType.CATEGORY_ADDED,
            competitorId = None,
            competitionId = command.competitionId,
            categoryId = Some(id),
            payload = Some(MessageInfo.Payload.CategoryAddedPayload(
              CategoryAddedPayload().withCategoryState(payload.getCategory.withId(id))
            ))
          ))
        } else {
          EitherT(CommandEventOperations[F, Event].error(CategoryAlreadyExists(id, payload.getCategory)))
        }
      competitorAddedEvents <- payload.competitors.toList.traverse(c =>
        EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event].create(
          `type` = EventType.COMPETITOR_CATEGORY_ADDED,
          competitorId = None,
          competitionId = command.competitionId,
          categoryId = Some(id),
          payload = Some(MessageInfo.Payload.CompetitorCategoryAddedPayload(
            CompetitorCategoryAddedPayload().withNewCategoryId(id).withFighterId(c)
          ))
        ))
      )
    } yield Seq(event) ++ competitorAddedEvents
    eventT.value
  }

}
