package compman.compsrv.logic.actors

import compman.compsrv.logic.Operations
import compman.compsrv.logic.actors.CommandProcessorOperations.KafkaTopicConfig
import compman.compsrv.logic.logging.CompetitionLogging.{Annotations, LIO, Live}
import compman.compsrv.model.{CompetitionProcessingStarted, CompetitionProcessingStopped, CompetitionState}
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.query.actors.{ActorSystem, Context, EventSourcedBehavior, Timers}
import compman.compsrv.query.actors
import compman.compsrv.query.actors.Messages._
import zio.{Fiber, RIO, Task, ZIO}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.kafka.consumer.Consumer
import zio.kafka.producer.Producer
import zio.logging.{LogAnnotation, Logging}

import java.util.UUID
import java.util.concurrent.TimeUnit

object CompetitionProcessorActor {
  import compman.compsrv.Main.Live._
  import compman.compsrv.logic.logging._
  import zio.interop.catz._
  private val DefaultTimerKey = "stopTimer"

  def behavior[Env](
    processorOperations: CommandProcessorOperations[Env],
    competitionId: String,
    eventTopic: String,
    actorIdleTimeoutMillis: Long = 300000
  ): EventSourcedBehavior[Env with Logging with Clock, CompetitionState, Message, EventDTO] =
    new EventSourcedBehavior[Env with Logging with Clock, CompetitionState, Message, EventDTO](competitionId) {

      override def postStop(
        actorConfig: ActorSystem.ActorConfig,
        context: Context[Message],
        state: CompetitionState,
        timers: Timers[Env with Logging with Clock, Message]
      ): RIO[Env with Logging with Clock, Unit] = for {
        _ <- processorOperations.sendNotifications(competitionId, Seq(CompetitionProcessingStopped(competitionId)))
      } yield (Seq.empty, Seq.empty)

      override def init(
        actorConfig: ActorSystem.ActorConfig,
        context: actors.Context[Message],
        initState: CompetitionState,
        timers: Timers[Env with Logging with Clock, Message]
      ): RIO[Env with Logging with Clock, (Seq[Fiber[Throwable, Unit]], Seq[Message[Any]])] = for {
        _ <-
          if (initState.competitionProperties.isEmpty) Task
            .fail(new RuntimeException(s"Competition properties are missing: $initState"))
          else Task.unit
        props = initState.competitionProperties.get
        started = CompetitionProcessingStarted(
          competitionId,
          eventTopic,
          props.getCreatorId,
          props.getCreationTimestamp,
          props.getStartDate,
          props.getEndDate,
          props.getTimeZone,
          props.getStatus
        )
        _ <- Logging.info(s"Sending notification: $started")
        _ <- processorOperations.sendNotifications(competitionId, Seq(started))
      } yield (Seq.empty, Seq.empty)

      override def receive[A](
        context: actors.Context[Message],
        actorConfig: ActorSystem.ActorConfig,
        state: CompetitionState,
        command: Message[A],
        timers: Timers[Env with Logging with Clock, Message]
      ): RIO[Env with Logging with Clock, (Command[EventDTO], CompetitionState => A)] = {
        val unit: CompetitionState => A = _ => ().asInstanceOf[A]
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
                    if (cmd.getId == null) RIO.fail(new IllegalArgumentException(s"Command $cmd has no ID"))
                    else RIO.unit
                  processResult <- Live.withContext(
                    _.annotate(LogAnnotation.CorrelationId, Option(cmd.getId).map(UUID.fromString))
                      .annotate(Annotations.competitionId, Option(cmd.getCompetitionId))
                  ) { Operations.processCommand[LIO](state, cmd) }
                  res <- processResult match {
                    case Left(value)  => info(s"Error: $value") *> ZIO.effect((Command.ignore, unit))
                    case Right(value) => ZIO.effect((Command.persist(value), unit))
                  }
                } yield res
              }
            case Stop => for {
                _ <- info(s"Stopping actor for competition $competitionId")
                _ <- context.stopSelf
              } yield (Command.ignore, unit)
          }
        } yield res
      }

      override def sourceEvent(
        state: CompetitionState,
        event: EventDTO
      ): RIO[Env with Logging with Clock, CompetitionState] = Live.withContext(
        _.annotate(LogAnnotation.CorrelationId, Option(UUID.fromString(event.getCorrelationId)))
          .annotate(Annotations.competitionId, Option(event.getCompetitionId))
      ) { Operations.applyEvent[LIO](state, event) }

      override def getEvents(
        persistenceId: String,
        state: CompetitionState
      ): RIO[Env with Logging with Clock, Seq[EventDTO]] = for {
        _      <- processorOperations.createTopicIfMissing(eventTopic, KafkaTopicConfig())
        events <- processorOperations.retrieveEvents(eventTopic, state.revision)
      } yield events

      override def persistEvents(persistenceId: String, events: Seq[EventDTO]): RIO[Env with Logging with Clock, Unit] =
        processorOperations.persistEvents(events)
    }

  sealed trait Message[+_]
  object Stop                                     extends Message[Unit]
  final case class ProcessCommand(fa: CommandDTO) extends Message[Unit]

  type CompProcessorEnv = Logging with Clock with Blocking with SnapshotService.Snapshot

  type LiveEnv = CompProcessorEnv with Consumer with Producer[Any, String, Array[Byte]]

}
