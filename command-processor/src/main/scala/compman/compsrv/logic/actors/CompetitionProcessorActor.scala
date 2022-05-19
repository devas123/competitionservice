package compman.compsrv.logic.actors

import compman.compsrv.logic.{CompetitionState, Operations}
import compman.compsrv.logic.actor.kafka.KafkaSupervisor._
import compman.compsrv.logic.actors.EventSourcedMessages._
import compman.compsrv.logic.logging.CompetitionLogging.{Annotations, LIO, Live}
import compman.compsrv.model.command.Commands
import compman.compsrv.model.command.Commands.createErrorCommandCallbackMessageParameters
import compservice.model.protobuf.command
import compservice.model.protobuf.event.Event
import compservice.model.protobuf.model.{CompetitionProcessingStarted, CompetitionProcessingStopped, CompetitionProcessorNotification}
import zio.{Fiber, Has, Promise, RIO, Task, ZIO}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.durationInt
import zio.kafka.consumer.Consumer
import zio.kafka.producer.Producer
import zio.logging.Logging

import java.util.UUID
import java.util.concurrent.TimeUnit

object CompetitionProcessorActor {
  import compman.compsrv.CommandProcessorMain.Live._
  import compman.compsrv.logic.logging._
  import zio.interop.catz._
  private val DefaultTimerKey = "stopTimer"

  def behavior[Env](
    competitionId: String,
    eventTopic: String,
    commandCallbackTopic: String,
    kafkaSupervisor: ActorRef[KafkaSupervisorCommand],
    competitionNotificationsTopic: String,
    actorIdleTimeoutMillis: Long
  ): EventSourcedBehavior[Env with Logging with Clock, CompetitionState, Message, Event] =
    new EventSourcedBehavior[Env with Logging with Clock, CompetitionState, Message, Event](competitionId) {

      override def postStop(
        actorConfig: ActorSystem.ActorConfig,
        context: Context[Message],
        state: CompetitionState,
        timers: Timers[Env with Logging with Clock, Message]
      ): RIO[Env with Logging with Clock, Unit] = for {
        _ <- kafkaSupervisor ! PublishMessage(
          competitionNotificationsTopic,
          competitionId,
          CompetitionProcessorNotification()
            .withStopped(CompetitionProcessingStopped(competitionId)).toByteArray
        )
      } yield ()

      override def init(
        actorConfig: ActorSystem.ActorConfig,
        context: Context[Message],
        initState: CompetitionState,
        timers: Timers[Env with Logging with Clock, Message]
      ): RIO[Env with Logging with Clock, (Seq[Fiber[Throwable, Unit]], Seq[Message])] = for {
        _ <- Task.fail(new RuntimeException(s"Competition properties are missing: $initState"))
          .when(initState.competitionProperties.isEmpty)
        props = initState.competitionProperties.get
        started = CompetitionProcessingStarted(
          competitionId,
          initState.competitionProperties.map(_.competitionName).getOrElse(""),
          eventTopic,
          props.creatorId,
          props.creationTimestamp,
          props.startDate,
          props.endDate,
          props.timeZone,
          props.status
        )
        _ <- Logging.info(s"Sending notification: $started")
        _ <- kafkaSupervisor !
          PublishMessage(competitionNotificationsTopic, competitionId, CompetitionProcessorNotification().withStarted(started).toByteArray)
        _ <- timers
          .startSingleTimer(DefaultTimerKey, zio.duration.Duration(actorIdleTimeoutMillis, TimeUnit.MILLISECONDS), Stop)
      } yield (Seq.empty, Seq.empty)

      override def receive(
        context: Context[Message],
        actorConfig: ActorSystem.ActorConfig,
        state: CompetitionState,
        command: Message,
        timers: Timers[Env with Logging with Clock, Message]
      ): RIO[Env with Logging with Clock, (EventSourcingCommand[Event], CompetitionState => Unit)] = {
        val unit: CompetitionState => Unit = _ => ()
        for {
          _ <- info(s"Received a command $command")
          res <- command match {
            case ProcessCommand(cmd) => timers.startSingleTimer(
                DefaultTimerKey,
                zio.duration.Duration(actorIdleTimeoutMillis, TimeUnit.MILLISECONDS),
                Stop
              ) *> {
                for {
                  _ <-
                  (info(s"Command $cmd has no ID") *> RIO.fail(new IllegalArgumentException(s"Command $cmd has no ID")))
                    .when(cmd.messageInfo.map(_.id).isEmpty)
                  _ <- info(s"Processing command $command")
                  processResult <- Live.withContext(
                    _.annotate(Annotations.correlationId, cmd.messageInfo.map(_.id))
                      .annotate(Annotations.competitionId, cmd.messageInfo.map(_.competitionId))
                  ) { Operations.processStatefulCommand[LIO](state, cmd) }
                  _ <- info(s"Processing done. ")
                  res <- processResult match {
                    case Left(value)  => info(s"Error: $value") *> (kafkaSupervisor ! PublishMessage(createErrorCommandCallbackMessageParameters(commandCallbackTopic, Commands.correlationId(cmd), value))) *> ZIO.effect((EventSourcingCommand.ignore, unit))
                    case Right(value) => ZIO.effect((EventSourcingCommand.persist(value), unit))
                  }
                } yield res
              }
            case Stop => for {
                _ <- info(s"Stopping actor for competition $competitionId")
                _ <- context.stopSelf
              } yield (EventSourcingCommand.ignore, unit)
          }
        } yield res
      }

      override def sourceEvent(
        state: CompetitionState,
        event: Event
      ): RIO[Env with Logging with Clock, CompetitionState] = Live.withContext(
        _.annotate(Annotations.correlationId, event.messageInfo.map(_.correlationId))
          .annotate(Annotations.competitionId, event.messageInfo.map(_.competitionId))
      ) { Operations.applyEvent[LIO](state, event) }

      override def getEvents(
        persistenceId: String,
        state: CompetitionState
      ): RIO[Env with Logging with Clock, Seq[Event]] = for {
        promise <- Promise.make[Throwable, Seq[Array[Byte]]]
        _       <- kafkaSupervisor ! CreateTopicIfMissing(eventTopic, KafkaTopicConfig())
        _       <- kafkaSupervisor ! QuerySync(eventTopic, UUID.randomUUID().toString, promise, 30.seconds)
        _       <- Logging.info(s"Getting events from topic: $eventTopic, starting from ${state.revision}")
        events <- promise.await.map(_.map(e => Event.parseFrom(e)))
          .onError(e => Logging.info(e.prettyPrint)).foldM(_ => RIO(Seq.empty), RIO(_))
        _ <- Logging.info(s"Done getting events! ${events.size} events were received.")
      } yield events

      override def persistEvents(
        persistenceId: String,
        events: Seq[Event]
      ): RIO[Env with Logging with Clock, Unit] = {
        import cats.implicits._
        events.traverse(e => kafkaSupervisor ! PublishMessage(eventTopic, competitionId, e.toByteArray))
          .unit
      }
    }

  sealed trait Message
  object Stop                                     extends Message
  final case class ProcessCommand(fa: command.Command) extends Message
  type CompProcessorEnv = Logging with Clock with Blocking with SnapshotService.Snapshot
  type LiveEnv          = CompProcessorEnv with Has[Consumer] with Has[Producer]
}
