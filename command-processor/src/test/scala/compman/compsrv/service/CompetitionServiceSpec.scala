package compman.compsrv.service

import compman.compsrv.logic.actors.{ActorSystem, _}
import compman.compsrv.logic.actors.CompetitionProcessorActor.{ProcessCommand, Stop}
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.model.{CommandProcessorNotification, CompetitionState}
import compman.compsrv.model.commands.{CommandDTO, CommandType}
import compman.compsrv.model.commands.payload.CreateCompetitionPayload
import compman.compsrv.model.dto.competition.{CompetitionPropertiesDTO, CompetitionStatus, RegistrationInfoDTO}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.events.payload.CompetitionCreatedPayload
import ActorSystem.ActorConfig
import zio.{Layer, Queue, Ref, ZIO}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.durationInt
import zio.kafka.consumer.ConsumerSettings
import zio.kafka.producer.ProducerSettings
import zio.logging.Logging
import zio.test._
import zio.test.Assertion._

import java.time.Instant
import java.util.UUID

object CompetitionServiceSpec extends DefaultRunnableSpec {
  object Deps {
    val competitionId   = "test-competition-id"
    val groupId: String = UUID.randomUUID().toString
    val clientId        = "client"
    private val brokers = List("localhost:9092")
    val consumerSettings: ConsumerSettings = ConsumerSettings(brokers).withGroupId(groupId).withClientId(clientId)
      .withCloseTimeout(30.seconds).withPollTimeout(10.millis).withProperty("enable.auto.commit", "false")
      .withProperty("auto.offset.reset", "earliest")

    val producerSettings: ProducerSettings = ProducerSettings(brokers)
    val loggingLayer: Layer[Nothing, Logging] = CompetitionLogging.Live.loggingLayer
    val snapshotLayer: Layer[Nothing, SnapshotService.Snapshot] = SnapshotService.test.toLayer
  }

  import Deps._
  import zio.test.environment._

  def spec: Spec[TestEnvironment, TestFailure[Throwable], TestSuccess] =
    suite("The Competition Processor should")(testM("Accept commands") {
      for {
        eventsQueue    <- Queue.unbounded[EventDTO]
        notificationQueue    <- Queue.unbounded[CommandProcessorNotification]
        snapshotsRef <- Ref.make(Map.empty[String, CompetitionState])
        actorSystem <- ActorSystem("Test")
        processorOperations <- ZIO.effect(CommandProcessorOperations.test(eventsQueue, notificationQueue, snapshotsRef))
        initialState <- processorOperations.getStateSnapshot(competitionId).flatMap(p => ZIO.effect(p.fold(processorOperations.createInitialState(competitionId, None))(identity)))
        processor <- actorSystem.make( s"CompetitionProcessor-$competitionId",
          ActorConfig(),
          initialState,
          CompetitionProcessorActor.behavior[Clock with Blocking with Logging](CommandProcessorOperations.test(eventsQueue, notificationQueue, snapshotsRef), competitionId, "test-events"))
        command = {
          val cmd = new CommandDTO()
          cmd.setId(UUID.randomUUID().toString)
          cmd.setType(CommandType.CREATE_COMPETITION_COMMAND)
          cmd.setCompetitionId(competitionId)
          cmd.setPayload(
            new CreateCompetitionPayload().setReginfo(
              new RegistrationInfoDTO().setId(competitionId).setRegistrationGroups(Array.empty)
                .setRegistrationPeriods(Array.empty).setRegistrationOpen(true)
            ).setProperties(
              new CompetitionPropertiesDTO().setId(competitionId).setCompetitionName("Test competition")
                .setStatus(CompetitionStatus.CREATED).setTimeZone("UTC").setStartDate(Instant.now())
                .setEndDate(Instant.now())
            )
          )
          cmd
        }
        _ <- processor ! ProcessCommand(command)
        f <- eventsQueue.takeN(1).fork
        _ <- processor ! Stop
        f1 <- notificationQueue.takeN(2).fork
        eventsO <- f.join.timeout(10.seconds)
        notificationsO <- f1.join.timeout(10.seconds)
        events = eventsO.getOrElse(List.empty)
        notifications = notificationsO.getOrElse(List.empty)
      } yield assert(events)(isNonEmpty) && assert(notifications)(isNonEmpty) &&
        assert(events.head.getType)(equalTo(EventType.COMPETITION_CREATED)) &&
        assert(events.head.getPayload)(not(isNull)) &&
        assert(events.head.getPayload)(isSubtype[CompetitionCreatedPayload](anything)) &&
        assert(events.head.getCorrelationId)(equalTo(command.getId)) &&
        assert(events.head.getLocalEventNumber.toLong)(equalTo(0L))
    }).provideLayer(loggingLayer ++ snapshotLayer ++ Clock.live ++ Blocking.live)
}
