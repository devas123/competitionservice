package compman.compsrv.logic.actors

import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.kafka.ConsumerMessage.PartitionOffset
import akka.kafka.ProducerMessage
import akka.stream.scaladsl.Flow
import akka.NotUsed
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.google.protobuf.timestamp.Timestamp
import com.google.protobuf.util.Timestamps
import compman.compsrv.logic.actor.kafka.persistence.{EventSourcingOperations, KafkaBasedEventSourcedBehavior}
import compman.compsrv.logic.actor.kafka.persistence.KafkaBasedEventSourcedBehavior.{
  KafkaBasedEventSourcedBehaviorApi,
  Stop
}
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{KafkaSupervisorCommand, PublishMessage, QuerySync}
import compman.compsrv.logic.Operations
import compman.compsrv.logic.actors.CompetitionProcessorActorV2.{createInitialState, DefaultTimerKey, KafkaProducerFlow}
import compman.compsrv.model.Errors
import compman.compsrv.model.command.Commands
import compman.compsrv.model.command.Commands.createErrorCommandCallbackMessageParameters
import compservice.model.protobuf.command.Command
import compservice.model.protobuf.event.Event
import compservice.model.protobuf.model._

import java.util.UUID
import scala.concurrent.{Await, Promise}
import scala.concurrent.duration.DurationInt

class CompetitionProcessorActorV2(
  competitionId: String,
  eventsTopic: String,
  commandCallbackTopic: String,
  competitionNotificationsTopic: String,
  context: ActorContext[KafkaBasedEventSourcedBehaviorApi],
  kafkaSupervisor: ActorRef[KafkaSupervisorCommand],
  snapshotService: SnapshotService.Service,
  timers: TimerScheduler[KafkaBasedEventSourcedBehaviorApi],
  kafkaProducerFlowOptional: Option[KafkaProducerFlow]
) extends KafkaBasedEventSourcedBehavior[CommandProcessorCompetitionState, Command, Event, Errors.Error](
      competitionId,
      eventsTopic,
      classOf[Command],
      context
    ) {

  timers.startSingleTimer(DefaultTimerKey, Stop, 30.seconds)
  implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

  var isNotificationSent = false

  import compman.compsrv.CommandProcessorMain.Live._

  override def operations: EventSourcingOperations[Command, Event, CommandProcessorCompetitionState, Errors.Error] =
    new EventSourcingOperations[Command, Event, CommandProcessorCompetitionState, Errors.Error] {
      override def processCommand(
        command: Command,
        state: CommandProcessorCompetitionState
      ): Either[Errors.Error, Seq[Event]] = Operations.processStatefulCommand[IO](state, command).unsafeRunSync()

      override def applyEvent(
        event: Event,
        state: CommandProcessorCompetitionState
      ): CommandProcessorCompetitionState = {
        val s = Operations.applyEvent[IO](state, event).unsafeRunSync()
        if (!isNotificationSent) {
          val started = CompetitionProcessingStarted(
            competitionId,
            s.competitionProperties.map(_.competitionName).getOrElse(""),
            eventsTopic,
            s.competitionProperties.map(_.creatorId).getOrElse(""),
            s.competitionProperties.flatMap(_.creationTimestamp),
            s.competitionProperties.flatMap(_.startDate),
            s.competitionProperties.flatMap(_.endDate),
            s.competitionProperties.map(_.timeZone).get,
            s.competitionProperties.map(_.status).get
          )
          kafkaSupervisor ! PublishMessage(
            competitionNotificationsTopic,
            competitionId,
            CompetitionProcessorNotification().withStarted(started).toByteArray
          )
          isNotificationSent = true
        }
        s
      }

      override def optionallySaveStateSnapshot(state: CommandProcessorCompetitionState): Unit = snapshotService
        .saveSnapshot(state)

      override def processError(
        command: Command,
        error: Errors.Error,
        state: CommandProcessorCompetitionState
      ): CommandProcessorCompetitionState = {
        context.log.error(s"Error while processing command: $error")
        kafkaSupervisor ! PublishMessage(
          createErrorCommandCallbackMessageParameters(commandCallbackTopic, Commands.correlationId(command), error)
        )
        state
      }
    }

  override protected def producerFlow: Flow[
    ProducerMessage.Envelope[String, Event, PartitionOffset],
    ProducerMessage.Results[String, Event, PartitionOffset],
    NotUsed
  ] = kafkaProducerFlowOptional.getOrElse(super.producerFlow)

  override def getEvents(startFrom: Long): Seq[Event] = {
    val promise = Promise[Seq[Array[Byte]]]()
    context.log.info(s"Getting events from topic: $eventsTopic, starting from $startFrom")
    kafkaSupervisor ! QuerySync(eventsTopic, UUID.randomUUID().toString, promise, 30.seconds, Some(startFrom))
    val events = Await.result(promise.future, 1.minute).map(Event.parseFrom)
    context.log.info(s"Done getting events! ${events.size} events were received.")
    events
  }

  override def getInitialState: CommandProcessorCompetitionState = snapshotService.loadSnapshot(competitionId)
    .getOrElse(createInitialState(competitionId))

  override def getLatestOffset(state: CommandProcessorCompetitionState): Long = state.revision.toLong

  override def serializeEvent(event: Event): Array[Byte] = event.toByteArray

  override def onSignal: PartialFunction[Signal, Behavior[KafkaBasedEventSourcedBehaviorApi]] = { case _: PostStop =>
    kafkaSupervisor ! PublishMessage(
      competitionNotificationsTopic,
      competitionId,
      CompetitionProcessorNotification().withStopped(CompetitionProcessingStopped(competitionId)).toByteArray
    )
    snapshotService.close()
    Behaviors.same
  }

  override def commandSideEffect(msg: KafkaBasedEventSourcedBehavior.CommandReceived): Unit = {
    timers.startSingleTimer(DefaultTimerKey, Stop, 30.seconds)
  }
}

object CompetitionProcessorActorV2 {
  private val DefaultTimerKey = "stopTimer"

  type KafkaProducerFlow = Flow[
    ProducerMessage.Envelope[String, Event, PartitionOffset],
    ProducerMessage.Results[String, Event, PartitionOffset],
    NotUsed
  ]

  def behavior(
    competitionId: String,
    eventsTopic: String,
    commandCallbackTopic: String,
    competitionNotificationsTopic: String,
    kafkaSupervisor: ActorRef[KafkaSupervisorCommand],
    snapshotService: SnapshotService.Service,
    kafkaProducerFlowOptional: Option[KafkaProducerFlow] = None
  ): Behavior[KafkaBasedEventSourcedBehaviorApi] = Behaviors.setup { context =>
    Behaviors.withTimers { timers =>
      new CompetitionProcessorActorV2(
        competitionId,
        eventsTopic,
        commandCallbackTopic,
        competitionNotificationsTopic,
        context,
        kafkaSupervisor,
        snapshotService,
        timers,
        kafkaProducerFlowOptional
      )
    }
  }

  private def now: Timestamp = Timestamp.fromJavaProto(Timestamps.fromMillis(System.currentTimeMillis()))

  def createInitialState(competitionId: String): CommandProcessorCompetitionState = {
    val defaultProperties = Option(
      CompetitionProperties().withId(competitionId).withStatus(CompetitionStatus.CREATED).withCreationTimestamp(now)
        .withBracketsPublished(false).withSchedulePublished(false).withStaffIds(Seq.empty)
        .withEmailNotificationsEnabled(false).withTimeZone("UTC")
    )
    val defaultRegInfo = Some(
      RegistrationInfo().withId(competitionId).withRegistrationGroups(Map.empty).withRegistrationPeriods(Map.empty)
        .withRegistrationOpen(false)
    )

    CommandProcessorCompetitionState(
      id = competitionId,
      competitors = Map.empty,
      competitionProperties = defaultProperties.map(_.withId(competitionId).withTimeZone("UTC"))
        .map(_.withStatus(CompetitionStatus.CREATED)).map(pr => if (pr.startDate.isEmpty) pr.withStartDate(now) else pr)
        .map(pr => if (pr.creationTimestamp.isEmpty) pr.withCreationTimestamp(now) else pr),
      stages = Map.empty,
      fights = Map.empty,
      categories = Map.empty,
      registrationInfo = defaultRegInfo,
      schedule = Some(Schedule().withId(competitionId).withMats(Seq.empty).withPeriods(Seq.empty))
    )
  }
}
