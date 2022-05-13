package compman.compsrv.logic.actors

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.jackson.ObjectMapperFactory
import compman.compsrv.logic.actor.kafka.KafkaSupervisor._
import compman.compsrv.logic.actors.EventSourcedMessages._
import compman.compsrv.logic.logging.CompetitionLogging.{Annotations, LIO, Live}
import compman.compsrv.logic.{CompetitionState, Operations}
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.{CompetitionProcessingStarted, CompetitionProcessingStopped}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.kafka.consumer.Consumer
import zio.kafka.producer.Producer
import zio.logging.{LogAnnotation, Logging}
import zio.{Fiber, Has, Promise, RIO, Task, ZIO}
import zio.duration.durationInt

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
    kafkaSupervisor: ActorRef[KafkaSupervisorCommand],
    competitionNotificationsTopic: String,
    mapper: ObjectMapper = ObjectMapperFactory.createObjectMapper,
    actorIdleTimeoutMillis: Long
  ): EventSourcedBehavior[Env with Logging with Clock, CompetitionState, Message, EventDTO] =
    new EventSourcedBehavior[Env with Logging with Clock, CompetitionState, Message, EventDTO](competitionId) {

      override def postStop(
        actorConfig: ActorSystem.ActorConfig,
        context: Context[Message],
        state: CompetitionState,
        timers: Timers[Env with Logging with Clock, Message]
      ): RIO[Env with Logging with Clock, Unit] = for {
        _ <- kafkaSupervisor ! PublishMessage(
          competitionNotificationsTopic,
          competitionId,
          mapper.writeValueAsBytes(CompetitionProcessingStopped(competitionId))
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
          initState.competitionProperties.map(_.getCompetitionName).getOrElse(""),
          eventTopic,
          props.getCreatorId,
          props.getCreationTimestamp,
          props.getStartDate,
          props.getEndDate,
          props.getTimeZone,
          props.getStatus
        )
        _ <- Logging.info(s"Sending notification: $started")
        _ <- kafkaSupervisor !
          PublishMessage(competitionNotificationsTopic, competitionId, mapper.writeValueAsBytes(started))
        _ <- timers
          .startSingleTimer(DefaultTimerKey, zio.duration.Duration(actorIdleTimeoutMillis, TimeUnit.MILLISECONDS), Stop)
      } yield (Seq.empty, Seq.empty)

      override def receive(
        context: Context[Message],
        actorConfig: ActorSystem.ActorConfig,
        state: CompetitionState,
        command: Message,
        timers: Timers[Env with Logging with Clock, Message]
      ): RIO[Env with Logging with Clock, (Command[EventDTO], CompetitionState => Unit)] = {
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
                    .when(cmd.getId == null)
                  _ <- info(s"Processing command $command")
                  processResult <- Live.withContext(
                    _.annotate(LogAnnotation.CorrelationId, Option(cmd.getId).map(UUID.fromString))
                      .annotate(Annotations.competitionId, Option(cmd.getCompetitionId))
                  ) { Operations.processStatefulCommand[LIO](state, cmd) }
                  _ <- info(s"Processing done. ")
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
        promise <- Promise.make[Throwable, Seq[Array[Byte]]]
        _       <- kafkaSupervisor ! CreateTopicIfMissing(eventTopic, KafkaTopicConfig())
        _       <- kafkaSupervisor ! QuerySync(eventTopic, UUID.randomUUID().toString, promise, 30.seconds)
        _       <- Logging.info(s"Getting events from topic: $eventTopic, starting from ${state.revision}")
        events <- promise.await.map(_.map(e => mapper.readValue(e, classOf[EventDTO])))
          .onError(e => Logging.info(e.prettyPrint)).foldM(_ => RIO(Seq.empty), RIO(_))
        _ <- Logging.info(s"Done getting events! ${events.size} events were received.")
      } yield events

      override def persistEvents(
        persistenceId: String,
        events: Seq[EventDTO]
      ): RIO[Env with Logging with Clock, Unit] = {
        import cats.implicits._
        events.traverse(e => kafkaSupervisor ! PublishMessage(eventTopic, competitionId, mapper.writeValueAsBytes(e)))
          .unit
      }
    }

  sealed trait Message
  object Stop                                     extends Message
  final case class ProcessCommand(fa: CommandDTO) extends Message
  type CompProcessorEnv = Logging with Clock with Blocking with SnapshotService.Snapshot
  type LiveEnv          = CompProcessorEnv with Has[Consumer] with Has[Producer]
}
