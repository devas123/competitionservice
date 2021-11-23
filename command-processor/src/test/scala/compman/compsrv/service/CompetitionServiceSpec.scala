package compman.compsrv.service

import compman.compsrv.jackson.ObjectMapperFactory
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{CreateTopicIfMissing, KafkaSupervisorCommand, PublishMessage, QuerySync}
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.CompetitionProcessorActor.ProcessCommand
import compman.compsrv.logic.actors._
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.model.commands.payload.CreateCompetitionPayload
import compman.compsrv.model.commands.{CommandDTO, CommandType}
import compman.compsrv.model.dto.competition.{CompetitionPropertiesDTO, CompetitionStatus, RegistrationInfoDTO}
import compman.compsrv.model.events.payload.CompetitionCreatedPayload
import compman.compsrv.model.events.{EventDTO, EventType}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.durationInt
import zio.kafka.consumer.ConsumerSettings
import zio.kafka.producer.ProducerSettings
import zio.logging.Logging
import zio.test.Assertion._
import zio.test._
import zio.{Layer, Ref, ZIO}

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
        snapshotsRef <- Ref.make(Map.empty[String, CompetitionState])
        actorSystem <- ActorSystem("Test")
        processorOperations <- ZIO.effect(CommandProcessorOperations.test(snapshotsRef))
        initialState <- processorOperations.getStateSnapshot(competitionId).flatMap(p => ZIO.effect(p.fold(processorOperations.createInitialState(competitionId, None))(identity)))
        kafkaSupervisor <- TestKit[KafkaSupervisorCommand](actorSystem)
        processor <- actorSystem.make( s"CompetitionProcessor-$competitionId",
          ActorConfig(),
          initialState,
          CompetitionProcessorActor.behavior[Clock with Blocking with Logging](competitionId, "test-events", kafkaSupervisor.ref, "test-notifications"))
        _ <- Logging.info(s"Created actor: $processor")
        _ <- kafkaSupervisor.expectMessageClass(3.seconds, classOf[CreateTopicIfMissing])
        msg <- kafkaSupervisor.expectMessageClass(3.seconds, classOf[QuerySync])
        unwrapped = msg.get
        promise = unwrapped.promise
        _ <- promise.succeed(Seq.empty)
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
        notificationOpt <- kafkaSupervisor.expectMessageClass(3.seconds, classOf[PublishMessage])
        notification = notificationOpt.get.message
        eventOpt <- kafkaSupervisor.expectMessageClass(3.seconds, classOf[PublishMessage])
        eventBytes = eventOpt.get.message
        mapper = ObjectMapperFactory.createObjectMapper
        event = mapper.readValue(eventBytes, classOf[EventDTO])
        _ <- TestClock.adjust(10.seconds)
      } yield assert(notification)(not(isNull)) &&
        assert(event.getType)(equalTo(EventType.COMPETITION_CREATED)) &&
        assert(event.getPayload)(not(isNull)) &&
        assert(event.getPayload)(isSubtype[CompetitionCreatedPayload](anything)) &&
        assert(event.getCorrelationId)(equalTo(command.getId)) &&
        assert(event.getLocalEventNumber.toLong)(equalTo(0L))
    }).provideLayer(loggingLayer ++ snapshotLayer ++ TestEnvironment.live)
}
