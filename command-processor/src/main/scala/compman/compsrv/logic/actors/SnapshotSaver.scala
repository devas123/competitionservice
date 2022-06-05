package compman.compsrv.logic.actors

import compservice.model.protobuf.model.CommandProcessorCompetitionState
import zio.clock.Clock
import zio.console.Console
import zio.logging.Logging

object SnapshotSaver {

  type SnapshotSaverEnv[Env] = Env with Logging with Clock with Console with SnapshotService.Snapshot

  import Behaviors._
  def behavior[Env](
    commandProcessorOperations: CommandProcessorOperations[Env]
  ): ActorBehavior[SnapshotSaverEnv[Env], Unit, SnapshotSaverMessage] = Behaviors
    .behavior[SnapshotSaverEnv[Env], Unit, SnapshotSaverMessage].withReceive { (_, _, _, message, _) =>
      message match {
        case SaveSnapshot(state) => for {
            _ <- Logging.info(s"Saving a snapshot for competition ${state.id}, version: ${state.revision}") *>
              commandProcessorOperations.saveStateSnapshot(state)
          } yield ()
      }
    }

  sealed trait SnapshotSaverMessage

  final case class SaveSnapshot(state: CommandProcessorCompetitionState) extends SnapshotSaverMessage
}
