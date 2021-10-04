package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.CompetitionProcessorActor.{LiveEnv, ProcessCommand}
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.query.actors.ActorSystem.ActorConfig
import compman.compsrv.query.actors.{ActorBehavior, ActorSystem, Context, Timers}
import zio.kafka.admin.{AdminClient, AdminClientSettings}
import zio.kafka.consumer.ConsumerSettings
import zio.logging.Logging
import zio.{RIO, Task, ZIO}

import java.util.UUID

object CompetitionProcessorSupervisorActor {

  def behavior(consumerSettings: ConsumerSettings, adminSettings: AdminClientSettings): ActorBehavior[LiveEnv, Unit, Message] = new ActorBehavior[LiveEnv, Unit, Message] {
    private val admin = AdminClient.make(adminSettings)

    private def createCommandProcessorConfig(adm: AdminClient, consumerSettings: ConsumerSettings) =
      CommandProcessorOperations[LiveEnv](adm, consumerSettings)

    override def receive[A](context: Context[Message], actorConfig: ActorSystem.ActorConfig, state: Unit, command: Message[A], timers: Timers[LiveEnv, Message]): RIO[LiveEnv, (Unit, A)] = {
      command match {
        case CommandReceived(competitionId, fa) =>
          for {
            _ <- Logging.info(s"Received command: $fa")
            actor <- admin.use { adm =>
              for {
                act <- context.findChild[CompetitionProcessorActor.Message](s"CompetitionProcessor-$competitionId").flatMap(optActor =>
                  optActor.map(exists => Logging.info(s"Found existing actor for competition $competitionId") *> ZIO.effect(exists)).getOrElse(
                    for {
                      _ <- Logging.info(s"Creating new actor for competition: $competitionId")
                      commandProcessorOperations <- createCommandProcessorConfig(
                        adm,
                        consumerSettings
                          .withClientId(UUID.randomUUID().toString)
                          .withGroupId(UUID.randomUUID().toString)
                      )
                      initialState <- commandProcessorOperations.getStateSnapshot(competitionId) >>=
                        (_.map(Task(_)).getOrElse(commandProcessorOperations.createInitialState(competitionId)))
                      a <- context.make(
                        s"CompetitionProcessor-$competitionId",
                        ActorConfig(),
                        initialState,
                        CompetitionProcessorActor
                          .behavior(commandProcessorOperations, competitionId, s"$competitionId-events")
                      )
                    } yield a
                  ))
              } yield act
            }
            _ <- for {
              _ <- Logging.info(s"Sending command $fa to existing actor")
              _ <- actor ! ProcessCommand(fa)
            } yield ()
          } yield ((), ().asInstanceOf[A])
      }
    }
  }

  sealed trait Message[+_]

  final case class CommandReceived(competitionId: String, fa: CommandDTO) extends Message[Unit]
}
