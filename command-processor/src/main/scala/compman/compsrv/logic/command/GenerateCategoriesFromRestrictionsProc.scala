package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import cats.implicits._
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.category.CategoryGenerateService
import compman.compsrv.model.Errors
import compman.compsrv.model.command.Commands.{
  GenerateCategoriesFromRestrictionsCommand,
  InternalCommandProcessorCommand
}
import compman.compsrv.model.Errors.NoPayloadError
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload.CategoryAddedPayload
import compservice.model.protobuf.model.CommandProcessorCompetitionState

object GenerateCategoriesFromRestrictionsProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x: GenerateCategoriesFromRestrictionsCommand => process[F](x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    command: GenerateCategoriesFromRestrictionsCommand,
    state: CommandProcessorCompetitionState
  ): F[Either[Errors.Error, Seq[Event]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      payload <- EitherT.fromOption[F](command.payload, NoPayloadError())
      categories = CategoryGenerateService.generateCategories(payload)
      exists     = categories.find(cat => state.categories.contains(cat.id))
      events <-
        if (exists.isDefined) {
          EitherT
            .fromEither[F](Left[Errors.Error, List[Event]](Errors.CategoryAlreadyExists(exists.get.id, exists.get)))
        } else {
          EitherT.liftF[F, Errors.Error, List[Event]](categories.traverse(cat =>
            CommandEventOperations[F, Event].create(
              `type` = EventType.CATEGORY_ADDED,
              competitorId = None,
              competitionId = command.competitionId,
              categoryId = Option(cat.id),
              payload = Option(MessageInfo.Payload.CategoryAddedPayload(CategoryAddedPayload().withCategoryState(cat)))
            )
          ))
        }
    } yield events
    eventT.value
  }
}
