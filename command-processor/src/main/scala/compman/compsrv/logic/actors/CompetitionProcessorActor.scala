package compman.compsrv.logic.actors

import compman.compsrv.logic.Operations
import compman.compsrv.logic.actor.kafka.KafkaSupervisor._
import compman.compsrv.logic.actors.EventSourcedMessages._
import compman.compsrv.logic.logging.CompetitionLogging.{Annotations, LIO, Live}
import compman.compsrv.model.command.Commands
import compman.compsrv.model.command.Commands.createErrorCommandCallbackMessageParameters
import compservice.model.protobuf.command
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.model.{CommandProcessorCompetitionState, CompetitionProcessingStarted, CompetitionProcessingStopped, CompetitionProcessorNotification}
import zio.{Fiber, Has, Promise, RIO, Task, URIO, ZIO}
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
  import zio.interop.catz._
  private val DefaultTimerKey = "stopTimer"

  final case class CompetitionProcessorActorState(
    competitionState: CommandProcessorCompetitionState,
    isNotificationSent: Boolean
  )

  final case class CompetitionProcessorActorProps(
    competitionId: String,
    eventTopic: String,
    commandCallbackTopic: String,
    kafkaSupervisor: ActorRef[KafkaSupervisorCommand],
    snapshotSaver: ActorRef[SnapshotSaver.SnapshotSaverMessage],
    competitionNotificationsTopic: String,
    actorIdleTimeoutMillis: Long,
    saveSnapshotInterval: Int = 10
  )

  def initialState(competitionState: CommandProcessorCompetitionState): CompetitionProcessorActorState =
    CompetitionProcessorActorState(competitionState, isNotificationSent = false)

  def behavior[Env](
    props: CompetitionProcessorActorProps
  ): EventSourcedBehavior[Env with Logging with Clock, CompetitionProcessorActorState, Message, Event] =
    new EventSourcedBehavior[Env with Logging with Clock, CompetitionProcessorActorState, Message, Event](
      props.competitionId
    ) {
      override def postStop(
        actorConfig: ActorSystem.ActorConfig,
        context: Context[Message],
        state: CompetitionProcessorActorState,
        timers: Timers[Env with Logging with Clock, Message]
      ): RIO[Env with Logging with Clock, Unit] = for {
        _ <- props.kafkaSupervisor ! PublishMessage(
          props.competitionNotificationsTopic,
          props.competitionId,
          CompetitionProcessorNotification().withStopped(CompetitionProcessingStopped(props.competitionId)).toByteArray
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
        _ <- timers.startSingleTimer(
          DefaultTimerKey,
          zio.duration.Duration(props.actorIdleTimeoutMillis, TimeUnit.MILLISECONDS),
          Stop
        )
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
          _ <- Logging.info(s"Received a command $command")
          res <- command match {
            case ProcessCommand(cmd) => timers.startSingleTimer(
                DefaultTimerKey,
                zio.duration.Duration(props.actorIdleTimeoutMillis, TimeUnit.MILLISECONDS),
                Stop
              ) *> {
                for {
                  _ <-
                  (Logging.info(s"Command $cmd has no ID") *>
                    RIO.fail(new IllegalArgumentException(s"Command $cmd has no ID")))
                    .when(cmd.messageInfo.map(_.id).isEmpty)
                  _ <- Logging.info(s"Command id is ${cmd.messageInfo.flatMap(_.id).get}")
                  processResult <- Live.withContext(
                    _.annotate(Annotations.correlationId, cmd.messageInfo.flatMap(_.id))
                      .annotate(Annotations.competitionId, cmd.messageInfo.flatMap(_.competitionId))
                  ) { Operations.processStatefulCommand[LIO](state.competitionState, cmd) }
                  _ <- Logging.info(s"Processing done. ")
                  res <- processResult match {
                    case Left(value) => Logging.info(s"Error: $value") *>
                        (props.kafkaSupervisor ! PublishMessage(createErrorCommandCallbackMessageParameters(
                          props.commandCallbackTopic,
                          Commands.correlationId(cmd),
                          value
                        ))) *> ZIO.effect((EventSourcingCommand.ignore, unit))
                    case Right(value) => ZIO.effect((EventSourcingCommand.persist(value), unit))
                  }
                } yield res
              }
            case Stop => for {
                _ <- Logging.info(s"Stopping actor for competition ${props.competitionId}")
                _ <- context.stopSelf
              } yield (EventSourcingCommand.ignore, unit)
          }
        } yield res
      }

      private def isNotCompetitionCreatedEvent(event: Event) = event.`type` != EventType.COMPETITION_CREATED

      override def sourceEvent(
        state: CompetitionProcessorActorState,
        event: Event
      ): RIO[Env with Logging with Clock, CompetitionProcessorActorState] = Live.withContext(
        _.annotate(Annotations.correlationId, event.messageInfo.flatMap(_.correlationId))
          .annotate(Annotations.competitionId, event.messageInfo.flatMap(_.competitionId))
      ) {
        for {
          _ <- ZIO.fail(new RuntimeException("State revision is 0 but the applied event is not COMPETITION_CREATED."))
            .when(state.competitionState.revision == 0 && isNotCompetitionCreatedEvent(event))
          s <- Operations.applyEvent[LIO](state.competitionState, event)
          res <-
            if (state.isNotificationSent) {
              for {
                _ <- (props.snapshotSaver ! SnapshotSaver.SaveSnapshot(s))
                  .when(s.revision > 0 && s.revision % props.saveSnapshotInterval == 0)
              } yield CompetitionProcessorActorState(s, state.isNotificationSent)
            } else {
              for {
                started <- ZIO.effect(CompetitionProcessingStarted(
                  props.competitionId,
                  s.competitionProperties.map(_.competitionName).getOrElse(""),
                  props.eventTopic,
                  s.competitionProperties.map(_.creatorId).getOrElse(""),
                  s.competitionProperties.flatMap(_.creationTimestamp),
                  s.competitionProperties.flatMap(_.startDate),
                  s.competitionProperties.flatMap(_.endDate),
                  s.competitionProperties.map(_.timeZone).get,
                  s.competitionProperties.map(_.status).get
                ))
                _ <- Logging.info(s"Sending notification: $started")
                _ <- props.kafkaSupervisor ! PublishMessage(
                  props.competitionNotificationsTopic,
                  props.competitionId,
                  CompetitionProcessorNotification().withStarted(started).toByteArray
                )
              } yield CompetitionProcessorActorState(s, isNotificationSent = true)
            }
        } yield res
      }

      override def getEvents(
        persistenceId: String,
        state: CompetitionProcessorActorState
      ): RIO[Env with Logging with Clock, Seq[Event]] = for {
        promise <- Promise.make[Throwable, Seq[Array[Byte]]]
        _       <- props.kafkaSupervisor ! CreateTopicIfMissing(props.eventTopic, KafkaTopicConfig())
        _ <- props.kafkaSupervisor ! QuerySync(
          props.eventTopic,
          UUID.randomUUID().toString,
          promise,
          30.seconds,
          Some(state.competitionState.revision.toLong)
        )
        _ <- Logging
          .info(s"Getting events from topic: ${props.eventTopic}, starting from ${state.competitionState.revision}")
        events <- promise.await.map(_.map(e => Event.parseFrom(e))).onError(e => Logging.info(e.prettyPrint))
          .foldM(_ => RIO(Seq.empty), RIO(_))
        _ <- Logging.info(s"Done getting events! ${events.size} events were received.")
        _ <- ZIO.fail(new RuntimeException(
          s"The state has revision 0, but the first event is not CREATE_COMPETITION, but ${events.headOption}"
        )).when(state.competitionState.revision == 0 && events.nonEmpty && isNotCompetitionCreatedEvent(events.head))
      } yield events

      override def persistEvents(persistenceId: String, events: Seq[Event]): URIO[Env with Logging with Clock, Unit] = {
        import cats.implicits._
        events
          .traverse(e => props.kafkaSupervisor ! PublishMessage(props.eventTopic, props.competitionId, e.toByteArray))
          .unit
      }.ignore
    }

  sealed trait Message
  object Stop                                          extends Message
  final case class ProcessCommand(fa: command.Command) extends Message
  type CompProcessorEnv = Logging with Clock with Blocking with SnapshotService.Snapshot
  type LiveEnv          = CompProcessorEnv with Has[Consumer] with Has[Producer]
}
