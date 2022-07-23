package compman.compsrv.logic

import cats.Monad
import compman.compsrv.logic.command.stateless.{AddAcademyProc, RemoveAcademyProc, UpdateAcademyProc}
import compman.compsrv.logic.fight.CompetitorSelectionUtils.Interpreter
import compman.compsrv.model.Errors
import compman.compsrv.model.command.Commands.InternalCommandProcessorCommand
import compservice.model.protobuf.event.Event

object StatelessCommandProcessors {
  import Operations._

  def process[F[+_]: Monad: IdOperations: EventOperations: Interpreter](
    command: InternalCommandProcessorCommand[Any]
  ): F[Either[Errors.Error, Seq[Event]]] = {
    Seq(AddAcademyProc(), UpdateAcademyProc(), RemoveAcademyProc()).reduce((a, b) => a.orElse(b)).apply(command)
  }
}
