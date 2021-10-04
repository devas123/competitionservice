package compman.compsrv.logic.actors

import compman.compsrv.config.CommandProcessorConfig
import compman.compsrv.logic.actors.CompetitionProcessorActor.LiveEnv
import compman.compsrv.model.{CommandProcessorNotification, CompetitionState}
import compman.compsrv.model.events.EventDTO
import zio.{Queue, Ref, RIO, Tag}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.kafka.admin.AdminClient
import zio.kafka.consumer.ConsumerSettings
import zio.logging.Logging

import java.util.UUID

trait CommandProcessorOperationsFactory[-Env] {
  def getCommandProcessorOperations[R: Tag]: RIO[R with Env, CommandProcessorOperations[Env]]
}

object CommandProcessorOperationsFactory {
  def live(adminClient: AdminClient, consumerSettings: ConsumerSettings, commandProcessorConfig: CommandProcessorConfig): CommandProcessorOperationsFactory[LiveEnv] =
    new CommandProcessorOperationsFactory[LiveEnv] {
      override def getCommandProcessorOperations[R: Tag]: RIO[R with LiveEnv, CommandProcessorOperations[LiveEnv]] =
        CommandProcessorOperations[LiveEnv](
          adminClient,
          consumerSettings.withClientId(UUID.randomUUID().toString).withGroupId(UUID.randomUUID().toString),
          commandProcessorConfig
        )
    }

  def test(
    eventReceiver: Queue[EventDTO],
    notificationReceiver: Queue[CommandProcessorNotification],
    stateSnapshots: Ref[Map[String, CompetitionState]],
    initialState: Option[CompetitionState] = None
  ): CommandProcessorOperationsFactory[Clock with Logging with Blocking] =
    new CommandProcessorOperationsFactory[Clock with Logging with Blocking] {
      override def getCommandProcessorOperations[R: Tag]
        : RIO[R with Clock with Logging with Blocking, CommandProcessorOperations[Clock with Logging with Blocking]] =
        RIO(CommandProcessorOperations.test(eventReceiver, notificationReceiver, stateSnapshots, initialState))
    }
}
