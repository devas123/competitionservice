package compman.compsrv.logic.actors.behavior

import compman.compsrv.logic.actors.{ActorBehavior, ActorRef, Context, Timers}
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.{logError, LIO}
import compman.compsrv.model
import compman.compsrv.model.{Mapping, Payload}
import compman.compsrv.model.Mapping.EventMapping
import compman.compsrv.model.events.EventDTO
import compman.compsrv.query.model._
import compman.compsrv.query.sede.ObjectMapperFactory
import compman.compsrv.query.service.event.EventProcessors
import compman.compsrv.query.service.kafka.EventStreamingService.EventStreaming
import compman.compsrv.query.service.repository._
import io.getquill.CassandraZioSession
import zio.{Fiber, Queue, Ref, RIO, Tag, ZIO}
import zio.clock.Clock
import zio.kafka.consumer.{CommittableRecord, Consumer, Offset}
import zio.logging.Logging
import zio.stream.ZStream

object CompetitionEventListener {
  sealed trait ApiCommand[+_]
  case class EventReceived(event: EventDTO, record: CommittableRecord[String, Array[Byte]]) extends ApiCommand[Unit]
  case class CommitOffset(offset: Offset)                                                   extends ApiCommand[Unit]
  case class SetQueue(queue: Queue[Offset])                                                   extends ApiCommand[Unit]

  trait ActorContext {
    implicit val eventMapping: Mapping.EventMapping[LIO]
    implicit val loggingLive: compman.compsrv.logic.logging.CompetitionLogging.Service[LIO]
    implicit val competitionQueryOperations: CompetitionQueryOperations[LIO]
    implicit val competitionUpdateOperations: CompetitionUpdateOperations[LIO]

  }

  case class Live(cassandraZioSession: CassandraZioSession) extends ActorContext {
    implicit val eventMapping: Mapping.EventMapping[LIO] = model.Mapping.EventMapping.live
    implicit val loggingLive: CompetitionLogging.Service[LIO] = compman.compsrv.logic.logging.CompetitionLogging.Live
      .live[Any]
    implicit val competitionQueryOperations: CompetitionQueryOperations[LIO] = CompetitionQueryOperations
      .live(cassandraZioSession)
    implicit val competitionUpdateOperations: CompetitionUpdateOperations[LIO] = CompetitionUpdateOperations
      .live(cassandraZioSession)
  }

  case class Test(
    competitionProperties: Option[Ref[Map[String, CompetitionProperties]]] = None,
    categories: Option[Ref[Map[String, Category]]] = None,
    competitors: Option[Ref[Map[String, Competitor]]] = None,
    fights: Option[Ref[Map[String, Fight]]] = None,
    periods: Option[Ref[Map[String, Period]]] = None,
    registrationPeriods: Option[Ref[Map[String, RegistrationPeriod]]] = None,
    registrationGroups: Option[Ref[Map[String, RegistrationGroup]]] = None,
    stages: Option[Ref[Map[String, StageDescriptor]]] = None
  ) extends ActorContext {
    implicit val eventMapping: Mapping.EventMapping[LIO] = model.Mapping.EventMapping.live
    implicit val loggingLive: CompetitionLogging.Service[LIO] = compman.compsrv.logic.logging.CompetitionLogging.Live
      .live[Any]
    implicit val competitionQueryOperations: CompetitionQueryOperations[LIO] = CompetitionQueryOperations.test(
      competitionProperties,
      categories,
      competitors,
      fights,
      periods,
      registrationPeriods,
      registrationGroups,
      stages
    )
    implicit val competitionUpdateOperations: CompetitionUpdateOperations[LIO] = CompetitionUpdateOperations.test(
      competitionProperties,
      categories,
      competitors,
      fights,
      periods,
      registrationPeriods,
      registrationGroups,
      stages
    )
  }

  private[behavior] case class ActorState(queue: Option[Queue[Offset]] = None)

  val initialState: ActorState = ActorState()

  def behavior[R: Tag](
    competitionId: String,
    eventStreaming: EventStreaming[R],
    topic: String,
    context: ActorContext,
    websocketConnectionSupervisor: ActorRef[WebsocketConnectionSupervisor.ApiCommand]
  ): ActorBehavior[R with Logging with Clock, ActorState, ApiCommand] =
    new ActorBehavior[R with Logging with Clock, ActorState, ApiCommand] {

      import context._
      import zio.interop.catz._
      override def receive[A](
        context: Context[ApiCommand],
        actorConfig: ActorConfig,
        state: ActorState,
        command: ApiCommand[A],
        timers: Timers[R with Logging with Clock, ApiCommand]
      ): RIO[R with Logging with Clock, (ActorState, A)] = {
        command match {
          case EventReceived(event, record) => {
              for {
                mapped <- EventMapping.mapEventDto[LIO](event)
                _      <- EventProcessors.applyEvent[LIO, Payload](mapped)
                _      <- (websocketConnectionSupervisor ! WebsocketConnectionSupervisor.EventReceived(event)).fork
                _      <- context.self ! CommitOffset(record.offset)
              } yield (state, ().asInstanceOf[A])
            }.onError(cause => logError(cause.squash))
          case CommitOffset(offset) =>
            Logging.info(s"Sending offset $offset.") *> state.queue.map(_.offer(offset)).getOrElse(RIO.unit).map(_ => (state, ().asInstanceOf[A]))
          case SetQueue(queue) =>
            Logging.info("Setting queue.") *> ZIO.effectTotal((state.copy(queue = Some(queue)), ().asInstanceOf[A]))
        }
      }

      override def init(
        actorConfig: ActorConfig,
        context: Context[ApiCommand],
        initState: ActorState,
        timers: Timers[R with Logging with Clock, ApiCommand]
      ): RIO[R with Logging with Clock, (Seq[Fiber[Throwable, Unit]], Seq[ApiCommand[Any]])] = for {
        mapper <- ZIO.effect(ObjectMapperFactory.createObjectMapper)
        queue <- Queue.unbounded[Offset]
        _ <- context.self ! SetQueue(queue)
        k <- (for {
          _ <- Logging.info(s"Starting stream for listening to competition events for topic: $topic")
          _ <- eventStreaming.getByteArrayStream(topic, s"query-service-$competitionId").mapM(record =>
            (for {
              event <- ZIO.effect(mapper.readValue(record.value, classOf[EventDTO]))
              _     <- context.self ! EventReceived(event, record)
            } yield ()).as(record.offset)
          ).runDrain
          _ <- Logging.info(s"Finished stream for listening to competition events for topic: $topic")
        } yield ()).fork
        l <- (for {
          _ <- Logging.info(s"Starting stream for committing offsets.")
          _ <- ZStream.fromQueue(queue).aggregateAsync(Consumer.offsetBatches).mapM(_.commit).runDrain
          _ <- Logging.info(s"Finished stream for committing offsets: $topic")
        } yield ()).fork
      } yield (Seq(k, l), Seq.empty[ApiCommand[Any]])
    }
}