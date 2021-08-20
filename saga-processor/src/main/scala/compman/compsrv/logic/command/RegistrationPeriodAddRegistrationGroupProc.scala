package compman.compsrv.logic.command

import cats.Monad
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.{CompetitionState, Errors, Payload}
import compman.compsrv.model.command.Commands.{CategoryRegistrationStatusChangeCommand, Command}
import compman.compsrv.model.events.EventDTO

object RegistrationPeriodAddRegistrationGroupProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
      state: CompetitionState
  ): PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = {
    case x @ CategoryRegistrationStatusChangeCommand(_, _, _) =>
      process(x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
      command: CategoryRegistrationStatusChangeCommand,
      state: CompetitionState
  ): F[Either[Errors.Error, Seq[EventDTO]]] = ???
}
