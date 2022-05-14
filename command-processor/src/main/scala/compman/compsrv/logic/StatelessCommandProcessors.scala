package compman.compsrv.logic

import cats.Monad
import compman.compsrv.logic.command.stateless.{AddAcademyProc, RemoveAcademyProc, UpdateAcademyProc}
import compman.compsrv.logic.fight.CompetitorSelectionUtils.Interpreter
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.model.{Errors, Payload}
import compman.compsrv.model.command.Commands.InternalCommandProcessorCommand
import compman.compsrv.model.events.EventDTO

object StatelessCommandProcessors {
  import Operations._

  def process[F[+_]: CompetitionLogging.Service: Monad: IdOperations: EventOperations: Interpreter, P <: Payload](
    command: InternalCommandProcessorCommand[P]
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    Seq(AddAcademyProc(), UpdateAcademyProc(), RemoveAcademyProc()).reduce((a, b) => a.orElse(b)).apply(command)
  }
}
