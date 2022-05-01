package compman.compsrv.logic.actors

import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.actors.CompetitionProcessorActor.LiveEnv
import zio.{Ref, RIO, Tag}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.logging.Logging

trait CommandProcessorOperationsFactory[-Env] {
  def getCommandProcessorOperations[R: Tag]: RIO[R with Env, CommandProcessorOperations[Env]]
}

object CommandProcessorOperationsFactory {
  def live(): CommandProcessorOperationsFactory[LiveEnv] =
    new CommandProcessorOperationsFactory[LiveEnv] {
      override def getCommandProcessorOperations[R: Tag]: RIO[R with LiveEnv, CommandProcessorOperations[LiveEnv]] =
        CommandProcessorOperations[LiveEnv]()
    }

  def test(
    stateSnapshots: Ref[Map[String, CompetitionState]],
    initialState: Option[CompetitionState] = None
  ): CommandProcessorOperationsFactory[Clock with Logging with Blocking] =
    new CommandProcessorOperationsFactory[Clock with Logging with Blocking] {
      override def getCommandProcessorOperations[R: Tag]
        : RIO[R with Clock with Logging with Blocking, CommandProcessorOperations[Clock with Logging with Blocking]] =
        RIO(CommandProcessorOperations.test(stateSnapshots, initialState))
    }
}
