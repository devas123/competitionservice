package compman.compsrv.logic.actors

import compman.compsrv.config.CommandProcessorConfig
import compman.compsrv.jackson.ObjectMapperFactory
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.KafkaSupervisorCommand
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.CompetitionProcessorActor.ProcessCommand
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.model.commands.CommandDTO
import zio.{Tag, ZIO}
import zio.clock.Clock
import zio.logging.Logging

object CompetitionProcessorSupervisorActor {

  import Behaviors._
  def behavior[Env: Tag](
    commandProcessorOperationsFactory: CommandProcessorOperationsFactory[Env],
    commandProcessorConfig: CommandProcessorConfig,
    kafkaSupervisor: ActorRef[KafkaSupervisorCommand]
  ): ActorBehavior[Env with Logging with Clock with SnapshotService.Snapshot, Unit, Message] = Behaviors
    .behavior[Env with Logging with Clock with SnapshotService.Snapshot, Unit, Message]
    .withReceive { (context, _, _, command, _) =>
      {
        command match {
          case CommandReceived(competitionId, fa) =>
            val actorName = s"CompetitionProcessor-$competitionId"
            (for {
              _ <- Logging.info(s"Received command: $fa")
              actor <- for {
                act <- context.findChild[CompetitionProcessorActor.Message](actorName).flatMap(optActor =>
                  optActor.map(existingActor =>
                    Logging.info(s"Found existing actor for competition $competitionId") *> ZIO.effect(existingActor)
                  ).getOrElse(for {
                    _                          <- Logging.info(s"Creating new actor for competition: $competitionId")
                    commandProcessorOperations <- commandProcessorOperationsFactory.getCommandProcessorOperations[Env]
                    initialState <- commandProcessorOperations.getStateSnapshot(competitionId) >>= {
                      case None => Logging
                          .info(s"State snapshot not found, creating initial state with payload ${fa.getPayload}") *>
                          ZIO
                            .effect(commandProcessorOperations.createInitialState(competitionId, Option(fa.getPayload)))
                      case Some(value) => ZIO.effect(value)
                    }
                    _      <- Logging.info(s"Resolved initial state of the competition is $initialState")
                    mapper <- ZIO.effectTotal(ObjectMapperFactory.createObjectMapper)
                    a <- context.make(
                      actorName,
                      ActorConfig(),
                      initialState,
                      CompetitionProcessorActor.behavior[Env](
                        competitionId,
                        s"$competitionId-${commandProcessorConfig.eventsTopicPrefix}",
                        kafkaSupervisor,
                        commandProcessorConfig.competitionNotificationsTopic,
                        mapper,
                        commandProcessorConfig.actorIdleTimeoutMillis.getOrElse(300000)
                      )
                    )
                  } yield a)
                )
              } yield act
              _ <- for {
                _ <- Logging.info(s"Sending command $fa to actor")
                _ <- actor ! ProcessCommand(fa)
              } yield ()
            } yield ()).onError(err => CompetitionLogging.logError(err.squashTrace))
        }
      }
    }

  sealed trait Message

  final case class CommandReceived(competitionId: String, fa: CommandDTO) extends Message
}
