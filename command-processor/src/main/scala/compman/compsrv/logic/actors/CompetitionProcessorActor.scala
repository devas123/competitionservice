package compman.compsrv.logic.actors

import compman.compsrv.logic.Operations
import compman.compsrv.logic.actor.kafka.KafkaSupervisor._
import compman.compsrv.logic.actors.EventSourcedMessages._
import compman.compsrv.logic.logging.CompetitionLogging.{Annotations, LIO, Live}
import compman.compsrv.model.command.Commands
import compman.compsrv.model.command.Commands.createErrorCommandCallbackMessageParameters
import compservice.model.protobuf.command
import compservice.model.protobuf.event.Event
import compservice.model.protobuf.model.{
  CommandProcessorCompetitionState,
  CompetitionProcessingStarted,
  CompetitionProcessingStopped,
  CompetitionProcessorNotification
}
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

  final case class CompetitionProcessorActorState(
    competitionState: CommandProcessorCompetitionState,
    isNotificationSent: Boolean,
    eventsSinceLastSnapshotSaved: Int
  )

  def initialState(competitionState: CommandProcessorCompetitionState): CompetitionProcessorActorState =
    CompetitionProcessorActorState(competitionState, isNotificationSent = false, 0)

  def behavior[Env](
    competitionId: String,
    eventTopic: String,
    commandCallbackTopic: String,
    kafkaSupervisor: ActorRef[KafkaSupervisorCommand],
    snapshotSaver: ActorRef[SnapshotSaver.SnapshotSaverMessage],
    competitionNotificationsTopic: String,
    actorIdleTimeoutMillis: Long
  ): EventSourcedBehavior[Env with Logging with Clock, CompetitionProcessorActorState, Message, Event] =
    new EventSourcedBehavior[Env with Logging with Clock, CompetitionProcessorActorState, Message, Event](
      competitionId
    ) {
      override def postStop(
        actorConfig: ActorSystem.ActorConfig,
        context: Context[Message],
        state: CompetitionProcessorActorState,
        timers: Timers[Env with Logging with Clock, Message]
      ): RIO[Env with Logging with Clock, Unit] = for {
        _ <- kafkaSupervisor ! PublishMessage(
          competitionNotificationsTopic,
          competitionId,
          CompetitionProcessorNotification().withStopped(CompetitionProcessingStopped(competitionId)).toByteArray
        )
      } yield ()

      override def init(
        actorConfig: ActorSystem.ActorConfig,
        context: Context[Message],
        initState: CompetitionProcessorActorState,
        timers: Timers[Env with Logging with Clock, Message]
      ): RIO[Env with Logging with Clock, (Seq[Fiber[Throwable, Unit]], Seq[Message])] = for {
        _ <- Task.fail(new RuntimeException(s"Competition properties are missing: $initState"))
          .when(initState.competitionState.competitionProperties.isEmpty)
        _ <- timers
          .startSingleTimer(DefaultTimerKey, zio.duration.Duration(actorIdleTimeoutMillis, TimeUnit.MILLISECONDS), Stop)
      } yield (Seq.empty, Seq.empty)

      override def receive(
        context: Context[Message],
        actorConfig: ActorSystem.ActorConfig,
        state: CompetitionProcessorActorState,
        command: Message,
        timers: Timers[Env with Logging with Clock, Message]
      ): RIO[Env with Logging with Clock, (EventSourcingCommand[Event], CompetitionProcessorActorState => Unit)] = {
        val unit: CompetitionProcessorActorState => Unit = _ => ()
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
                  _ <- info(s"Command id is ${cmd.messageInfo.flatMap(_.id).get}")
                  processResult <- Live.withContext(
                    _.annotate(Annotations.correlationId, cmd.messageInfo.flatMap(_.id))
                      .annotate(Annotations.competitionId, cmd.messageInfo.flatMap(_.competitionId))
                  ) { Operations.processStatefulCommand[LIO](state.competitionState, cmd) }
                  _ <- info(s"Processing done. ")
                  res <- processResult match {
                    case Left(value) => info(s"Error: $value") *>
                        (kafkaSupervisor ! PublishMessage(createErrorCommandCallbackMessageParameters(
                          commandCallbackTopic,
                          Commands.correlationId(cmd),
                          value
                        ))) *> ZIO.effect((EventSourcingCommand.ignore, unit))
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
        state: CompetitionProcessorActorState,
        event: Event
      ): RIO[Env with Logging with Clock, CompetitionProcessorActorState] = Live.withContext(
        _.annotate(Annotations.correlationId, event.messageInfo.flatMap(_.correlationId))
          .annotate(Annotations.competitionId, event.messageInfo.flatMap(_.competitionId))
      ) {
        Operations.applyEvent[LIO](state.competitionState, event).flatMap(s =>
          if (state.isNotificationSent) {
            for {
              _ <- (snapshotSaver ! SnapshotSaver.SaveSnapshot(s))
                .when(state.eventsSinceLastSnapshotSaved > 10) // TODO: move magic number to config
              newEventsSinceLastSnapshotSaved =
                if (state.eventsSinceLastSnapshotSaved > 10) 0 else state.eventsSinceLastSnapshotSaved + 1
              newState <- ZIO
                .effect(CompetitionProcessorActorState(s, state.isNotificationSent, newEventsSinceLastSnapshotSaved))
            } yield newState
          } else {
            for {
              started <- ZIO.effect(CompetitionProcessingStarted(
                competitionId,
                s.competitionProperties.map(_.competitionName).getOrElse(""),
                eventTopic,
                s.competitionProperties.map(_.creatorId).getOrElse(""),
                s.competitionProperties.flatMap(_.creationTimestamp),
                s.competitionProperties.flatMap(_.startDate),
                s.competitionProperties.flatMap(_.endDate),
                s.competitionProperties.map(_.timeZone).get,
                s.competitionProperties.map(_.status).get
              ))
              _ <- Logging.info(s"Sending notification: $started")
              _ <- kafkaSupervisor ! PublishMessage(
                competitionNotificationsTopic,
                competitionId,
                CompetitionProcessorNotification().withStarted(started).toByteArray
              )
            } yield CompetitionProcessorActorState(s, isNotificationSent = true, state.eventsSinceLastSnapshotSaved + 1)
          }
        )
      }

      override def getEvents(
        persistenceId: String,
        state: CompetitionProcessorActorState
      ): RIO[Env with Logging with Clock, Seq[Event]] = for {
        promise <- Promise.make[Throwable, Seq[Array[Byte]]]
        _       <- kafkaSupervisor ! CreateTopicIfMissing(eventTopic, KafkaTopicConfig())
        _       <- kafkaSupervisor ! QuerySync(eventTopic, UUID.randomUUID().toString, promise, 30.seconds, Some(state.competitionState.revision.toLong))
        _ <- Logging.info(s"Getting events from topic: $eventTopic, starting from ${state.competitionState.revision}")
        events <- promise.await.map(_.map(e => Event.parseFrom(e))).onError(e => Logging.info(e.prettyPrint))
          .foldM(_ => RIO(Seq.empty), RIO(_))
        _ <- Logging.info(s"Done getting events! ${events.size} events were received.")
      } yield events

      override def persistEvents(persistenceId: String, events: Seq[Event]): RIO[Env with Logging with Clock, Unit] = {
        import cats.implicits._
        events.traverse(e => kafkaSupervisor ! PublishMessage(eventTopic, competitionId, e.toByteArray)).unit
      }
    }

  sealed trait Message
  object Stop                                          extends Message
  final case class ProcessCommand(fa: command.Command) extends Message
  type CompProcessorEnv = Logging with Clock with Blocking with SnapshotService.Snapshot
  type LiveEnv          = CompProcessorEnv with Has[Consumer] with Has[Producer]
}
