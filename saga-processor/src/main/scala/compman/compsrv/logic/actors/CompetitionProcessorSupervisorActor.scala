package compman.compsrv.logic.actors

import compman.compsrv.config.CommandProcessorConfig
import compman.compsrv.logic.actors.CompetitionProcessorActor.ProcessCommand
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.query.actors.{ActorBehavior, ActorSystem, Context, Timers}
import compman.compsrv.query.actors.ActorSystem.ActorConfig
import zio.{RIO, Tag, Task, ZIO}
import zio.clock.Clock
import zio.logging.Logging

object CompetitionProcessorSupervisorActor {

  def behavior[Env: Tag](
    commandProcessorOperationsFactory: CommandProcessorOperationsFactory[Env],
    commandProcessorConfig: CommandProcessorConfig
  ): ActorBehavior[Env with Logging with Clock with SnapshotService.Snapshot, Unit, Message] =
    new ActorBehavior[Env with Logging with Clock with SnapshotService.Snapshot, Unit, Message] {
      override def receive[A](
        context: Context[Message],
        actorConfig: ActorSystem.ActorConfig,
        state: Unit,
        command: Message[A],
        timers: Timers[Env with Logging with Clock with SnapshotService.Snapshot, Message]
      ): RIO[Env with Logging with Clock with SnapshotService.Snapshot, (Unit, A)] = {
        command match {
          case CommandReceived(competitionId, fa) => (for {
              _ <- Logging.info(s"Received command: $fa")
              actor <- for {
                act <- context.findChild[CompetitionProcessorActor.Message](s"CompetitionProcessor-$competitionId")
                  .flatMap(optActor =>
                    optActor.map(exists =>
                      Logging.info(s"Found existing actor for competition $competitionId") *> ZIO.effect(exists)
                    ).getOrElse(for {
                      _                          <- Logging.info(s"Creating new actor for competition: $competitionId")
                      commandProcessorOperations <- commandProcessorOperationsFactory.getCommandProcessorOperations[Env]
                      initialState <- commandProcessorOperations.getStateSnapshot(competitionId) >>=
                        (_.map(Task(_)).getOrElse(commandProcessorOperations.createInitialState(competitionId)))
                      a <- context.make(
                        s"CompetitionProcessor-$competitionId",
                        ActorConfig(),
                        initialState,
                        CompetitionProcessorActor.behavior[Env](
                          commandProcessorOperations,
                          competitionId,
                          s"$competitionId-${commandProcessorConfig.eventsTopicPrefix}"
                        )
                      )
                    } yield a)
                  )
              } yield act
              _ <- for {
                _ <- Logging.info(s"Sending command $fa to actor")
                _ <- actor ! ProcessCommand(fa)
              } yield ()
            } yield ((), ().asInstanceOf[A])).onError(Logging.error(s"Error while processing message $fa", _))
        }
      }
    }

  sealed trait Message[+_]

  final case class CommandReceived(competitionId: String, fa: CommandDTO) extends Message[Unit]
}
