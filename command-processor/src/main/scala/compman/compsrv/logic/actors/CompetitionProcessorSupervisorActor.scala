package compman.compsrv.logic.actors

import compman.compsrv.config.CommandProcessorConfig
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.KafkaSupervisorCommand
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.CompetitionProcessorActor.ProcessCommand
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.CompetitionEventsTopic
import compman.compsrv.logic.Operations.IdOperations
import compservice.model.protobuf.command.Command
import zio.{Tag, ZIO}
import zio.clock.Clock
import zio.console.Console
import zio.logging.Logging

object CompetitionProcessorSupervisorActor {

  type CompetitionProcessorSupervisorEnv[Env] = Env with Logging with Clock with SnapshotService.Snapshot with Console

  import Behaviors._

  def behavior[Env: Tag](
    commandProcessorOperations: CommandProcessorOperations[Env],
    commandProcessorConfig: CommandProcessorConfig,
    kafkaSupervisor: ActorRef[KafkaSupervisorCommand],
    snapshotSaver: ActorRef[SnapshotSaver.SnapshotSaverMessage]
  ): ActorBehavior[CompetitionProcessorSupervisorEnv[Env], Unit, Message] = Behaviors
    .behavior[CompetitionProcessorSupervisorEnv[Env], Unit, Message]
    .withReceive { (context, _, _, command, _) =>
      {
        command match {
          case CommandReceived(competitionId, fa) =>
            val actorName         = s"CompetitionProcessor-$competitionId"
            (for {
              _ <- Logging.info(s"Received command: $fa")
              actor <- for {
                act <- context.findChild[CompetitionProcessorActor.Message](actorName).flatMap(optActor =>
                  optActor.map(existingActor =>
                    Logging.info(s"Found existing actor for competition $competitionId") *> ZIO.effect(existingActor)
                  ).getOrElse(for {
                    _                          <- Logging.info(s"Creating new actor for competition: $competitionId")
                    initialState <- commandProcessorOperations.getStateSnapshot(competitionId) >>= {
                      case None => Logging.info(
                          s"State snapshot not found, creating initial state with payload ${fa.messageInfo.map(_.payload)}"
                        ) *> ZIO.effect(
                          commandProcessorOperations.createInitialState(competitionId, fa.messageInfo.map(_.payload))
                        )
                      case Some(value) => ZIO.effect(value)
                    }
                    _ <- Logging.info(s"Resolved initial state of the competition. It has revision: ${initialState.revision}")
                    a <- context.make(
                      actorName,
                      ActorConfig(),
                      CompetitionProcessorActor.initialState(initialState),
                      CompetitionProcessorActor.behavior[Env](
                        competitionId,
                        CompetitionEventsTopic(commandProcessorConfig.eventsTopicPrefix)(competitionId),
                        commandProcessorConfig.commandCallbackTopic,
                        kafkaSupervisor,
                        snapshotSaver,
                        commandProcessorConfig.competitionNotificationsTopic,
                        commandProcessorConfig.actorIdleTimeoutMillis.getOrElse(30 * 60000)
                      )
                    )
                  } yield a)
                )
              } yield act
              _ <- for {
                _ <- Logging.info(s"Sending command $fa to actor $actor")
                _ <- actor ! ProcessCommand(fa.update(_.messageInfo.setIfDefined(
                  fa.messageInfo.map(_.update(_.id := fa.messageInfo.flatMap(_.id).getOrElse(IdOperations.uid)))
                )))
              } yield ()
            } yield ()).onError(err => CompetitionLogging.logError(err.squashTrace))
        }
      }
    }

  sealed trait Message

  final case class CommandReceived(competitionId: String, fa: Command) extends Message
}
