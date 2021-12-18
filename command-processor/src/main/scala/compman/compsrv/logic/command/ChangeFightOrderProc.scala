package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic.{assertETErr, CompetitionState}
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.{Errors, Payload}
import compman.compsrv.model.command.Commands.{ChangeFightOrderCommand, Command}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.Errors.NoPayloadError
import compman.compsrv.model.dto.competition.FightStatus

object ChangeFightOrderProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = {
    case x @ ChangeFightOrderCommand(_, _, _) => process(x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    command: ChangeFightOrderCommand,
    state: CompetitionState
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] = for {
      payload <- EitherT.fromOption(command.payload, NoPayloadError())
      fightToMove  = state.fights.flatMap(_.get(payload.getFightId))
      newMatExists = state.schedule.exists(sched => sched.getMats.exists(mat => payload.getNewMatId == mat.getId))
      _ <- assertETErr(fightToMove.nonEmpty, Errors.FightDoesNotExist(payload.getFightId))
      _ <- assertETErr(newMatExists, Errors.MatDoesNotExist(payload.getNewMatId))
      _ <- assertETErr(
        !fightToMove.exists(f => Seq(FightStatus.IN_PROGRESS, FightStatus.FINISHED).contains(f.getStatus)),
        Errors.FightCannotBeMoved(payload.getFightId)
      )
      event <- EitherT.liftF[F, Errors.Error, EventDTO](CommandEventOperations[F, EventDTO, EventType].create(
        `type` = EventType.FIGHT_ORDER_CHANGED,
        competitorId = None,
        competitionId = command.competitionId,
        categoryId = command.categoryId,
        payload = command.payload
      ))
    } yield Seq(event)
    eventT.value
  }
}
