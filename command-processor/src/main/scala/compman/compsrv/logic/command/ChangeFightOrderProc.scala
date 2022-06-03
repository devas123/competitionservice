package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic.assertETErr
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.Errors
import compman.compsrv.model.command.Commands.{ChangeFightOrderCommand, InternalCommandProcessorCommand}
import compman.compsrv.model.Errors.NoPayloadError
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.model.{CommandProcessorCompetitionState, FightStatus}

object ChangeFightOrderProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x @ ChangeFightOrderCommand(_, _, _) => process(x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    command: ChangeFightOrderCommand,
    state: CommandProcessorCompetitionState
  ): F[Either[Errors.Error, Seq[Event]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      payload <- EitherT.fromOption(command.payload, NoPayloadError())
      fightToMove  = state.fights.get(payload.fightId)
      newMatExists = state.schedule.exists(sched => sched.mats.exists(mat => payload.newMatId == mat.id))
      _ <- assertETErr(fightToMove.nonEmpty, Errors.FightDoesNotExist(payload.fightId))
      _ <- assertETErr(newMatExists, Errors.MatDoesNotExist(payload.newMatId))
      _ <- assertETErr(
        !fightToMove.exists(f => Seq(FightStatus.IN_PROGRESS, FightStatus.FINISHED).contains(f.status)),
        Errors.FightCannotBeMoved(payload.fightId)
      )
      event <- EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event].create(
        `type` = EventType.FIGHT_ORDER_CHANGED,
        competitorId = None,
        competitionId = command.competitionId,
        categoryId = command.categoryId,
        payload = Some(MessageInfo.Payload.ChangeFightOrderPayload(payload))
      ))
    } yield Seq(event)
    eventT.value
  }
}
