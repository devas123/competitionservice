package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.Errors
import compman.compsrv.model.command.Commands.{ChangeCompetitorCategoryCommand, InternalCommandProcessorCommand}
import compman.compsrv.model.Errors.NoPayloadError
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}

object ChangeCompetitorCategoryProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CompetitionState
  ): PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x @ ChangeCompetitorCategoryCommand(_, _, _) => process(x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    command: ChangeCompetitorCategoryCommand,
    state: CompetitionState
  ): F[Either[Errors.Error, Seq[Event]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      payload <- EitherT.fromOption(command.payload, NoPayloadError())
      newCategoryExists = state.categories
        .exists(_.keySet.intersect(payload.newCategories.toSet) == payload.newCategories.toSet)
      fighterExists = state.competitors.exists(_.contains(payload.fighterId))
      event <-
        if (payload.newCategories.isEmpty) {
          EitherT.fromEither(Left[Errors.Error, Event](Errors.CategoryListIsEmpty()))
        } else if (!fighterExists) {
          EitherT.fromEither(Left[Errors.Error, Event](Errors.CompetitorDoesNotExist(payload.fighterId)))
        } else if (!newCategoryExists) {
          EitherT.fromEither(Left[Errors.Error, Event](Errors.CategoryDoesNotExist(
            state.categories.map(cs => payload.newCategories.toSet.diff(cs.keySet)).getOrElse(Set.empty).toSeq
          )))
        } else {
          EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event, EventType].create(
            `type` = EventType.COMPETITOR_CATEGORY_CHANGED,
            competitorId = None,
            competitionId = command.competitionId,
            categoryId = command.categoryId,
            payload = Some(MessageInfo.Payload.ChangeCompetitorCategoryPayload(payload))
          ))
        }
    } yield Seq(event)
    eventT.value
  }
}
