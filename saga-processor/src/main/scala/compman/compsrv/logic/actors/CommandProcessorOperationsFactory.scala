package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.CompetitionProcessorActor.LiveEnv
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.{CommandProcessorNotification, CompetitionState}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.kafka.admin.AdminClient
import zio.kafka.consumer.ConsumerSettings
import zio.logging.Logging
import zio.{Queue, RIO, Ref, Tag}

trait CommandProcessorOperationsFactory[-Env] {
  def getCommandProcessorOperations[R: Tag]: RIO[R with Env, CommandProcessorOperations[Env]]
}

object CommandProcessorOperationsFactory {
  def live(adminClient: AdminClient, consumerSettings: ConsumerSettings): CommandProcessorOperationsFactory[LiveEnv] = new CommandProcessorOperationsFactory[LiveEnv] {
    override def getCommandProcessorOperations[R: Tag]: RIO[R with LiveEnv, CommandProcessorOperations[LiveEnv]] = CommandProcessorOperations[LiveEnv](adminClient, consumerSettings)
  }

  def test(
            eventReceiver: Queue[EventDTO],
            notificationReceiver: Queue[CommandProcessorNotification],
            stateSnapshots: Ref[Map[String, CompetitionState]],
            initialState: Option[CompetitionState] = None
          ): CommandProcessorOperationsFactory[Clock with Logging with Blocking] = new CommandProcessorOperationsFactory[Clock with Logging with Blocking] {
    override def getCommandProcessorOperations[R: Tag]: RIO[R with Clock with Logging with Blocking, CommandProcessorOperations[Clock with Logging with Blocking]] = RIO(CommandProcessorOperations.test(eventReceiver, notificationReceiver, stateSnapshots, initialState))
  }
}
