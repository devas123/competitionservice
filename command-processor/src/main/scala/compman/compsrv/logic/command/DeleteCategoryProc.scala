package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.command.Commands.{DeleteCategoryCommand, InternalCommandProcessorCommand}
import compman.compsrv.model.Errors
import compman.compsrv.model.Errors.NoCategoryIdError
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}

object DeleteCategoryProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations]()
    : PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x: DeleteCategoryCommand => process(x)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    command: DeleteCategoryCommand
  ): F[Either[Errors.Error, Seq[Event]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      _ <- logic.assertETErr[F](command.categoryId.isDefined, NoCategoryIdError())
      event <- EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event, EventType].create(
        `type` = EventType.CATEGORY_DELETED,
        competitorId = None,
        competitionId = command.competitionId,
        categoryId = command.categoryId,
        payload = Some(MessageInfo.Payload.Empty)
      ))
    } yield Seq(event)
    eventT.value
  }
}
