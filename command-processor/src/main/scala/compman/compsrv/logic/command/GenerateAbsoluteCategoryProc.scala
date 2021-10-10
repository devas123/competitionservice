package compman.compsrv.logic.command

import cats.Monad
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.{CompetitionState, Errors, Payload}
import compman.compsrv.model.command.Commands.{Command, GenerateAbsoluteCategoryCommand}
import compman.compsrv.model.events.EventDTO

object GenerateAbsoluteCategoryProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
      state: CompetitionState
  ): PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = {
    case x: GenerateAbsoluteCategoryCommand =>
      ???
  }
}
