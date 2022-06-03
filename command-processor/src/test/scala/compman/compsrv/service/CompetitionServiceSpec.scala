package compman.compsrv.service

import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{
  CreateTopicIfMissing,
  KafkaSupervisorCommand,
  PublishMessage,
  QuerySync
}
import compman.compsrv.logic.actors._
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.CompetitionProcessorActor.ProcessCommand
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.model.extensions.InstantOps
import compservice.model.protobuf.command.{Command, CommandType}
import compservice.model.protobuf.commandpayload.CreateCompetitionPayload
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.model._
import zio.{Layer, Ref, ZIO}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.durationInt
import zio.kafka.consumer.ConsumerSettings
import zio.kafka.producer.ProducerSettings
import zio.logging.Logging
import zio.test._

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

    val producerSettings: ProducerSettings                      = ProducerSettings(brokers)
    val loggingLayer: Layer[Nothing, Logging]                   = CompetitionLogging.Live.loggingLayer
    val snapshotLayer: Layer[Nothing, SnapshotService.Snapshot] = SnapshotService.test.toLayer
  }

  import Deps._
  import zio.test.environment._

  def spec: Spec[TestEnvironment, TestFailure[Throwable], TestSuccess] =
    suite("The Competition Processor should")(testM("Accept commands") {
      ActorSystem("Test").use { actorSystem =>
        for {
          snapshotsRef        <- Ref.make(Map.empty[String, CompetitionState])
          processorOperations <- ZIO.effect(CommandProcessorOperations.test(snapshotsRef))
          initialState <- processorOperations.getStateSnapshot(competitionId)
            .flatMap(p => ZIO.effect(p.fold(processorOperations.createInitialState(competitionId, None))(identity)))
          kafkaSupervisor <- TestKit[KafkaSupervisorCommand](actorSystem)
          snapshotSaver <- TestKit[SnapshotSaver.SnapshotSaverMessage](actorSystem)
          processor <- actorSystem.make(
            s"CompetitionProcessor-$competitionId",
            ActorConfig(),
            CompetitionProcessorActor.initialState(initialState),
            CompetitionProcessorActor.behavior[Clock with Blocking with Logging](
              competitionId,
              "test-events",
              "test-commands-callback",
              kafkaSupervisor.ref,
              snapshotSaver.ref,
              "test-notifications",
              actorIdleTimeoutMillis = 10000L
            )
          )
          _   <- kafkaSupervisor.expectMessageClass(3.seconds, classOf[CreateTopicIfMissing])
          msg <- kafkaSupervisor.expectMessageClass(3.seconds, classOf[QuerySync])
          unwrapped = msg.get
          promise   = unwrapped.promise
          _ <- promise.succeed(Seq.empty)
          command = Command().withMessageInfo(
            MessageInfo().withId(UUID.randomUUID().toString).withCompetitionId(competitionId)
              .withPayload(MessageInfo.Payload.CreateCompetitionPayload(
                CreateCompetitionPayload().withReginfo(
                  RegistrationInfo().withId(competitionId).withRegistrationGroups(Map.empty[String, RegistrationGroup])
                    .withRegistrationPeriods(Map.empty[String, RegistrationPeriod]).withRegistrationOpen(true)
                ).withProperties(
                  CompetitionProperties().withId(competitionId).withCompetitionName("Test competition")
                    .withStatus(CompetitionStatus.CREATED).withTimeZone("UTC").withStartDate(Instant.now().asTimestamp)
                    .withEndDate(Instant.now().asTimestamp)
                )
              ))
          ).withType(CommandType.CREATE_COMPETITION_COMMAND)
          _               <- processor ! ProcessCommand(command)
          eventOpt <- kafkaSupervisor.expectMessageClass(3.seconds, classOf[PublishMessage])
          notificationOpt <- kafkaSupervisor.expectMessageClass(3.seconds, classOf[PublishMessage])
          notification = notificationOpt.get.message
          eventBytes = eventOpt.get.message
          event      = Event.parseFrom(eventBytes)
        } yield assertTrue(notification != null) && assertTrue(event.`type` == EventType.COMPETITION_CREATED) &&
          assertTrue(event.messageInfo.flatMap(_.payload.competitionCreatedPayload).isDefined) &&
          assertTrue(event.messageInfo.map(_.correlationId).contains(command.messageInfo.map(_.id).get)) &&
          assertTrue(event.localEventNumber.toLong == 0L)
      }
    }).provideLayer(loggingLayer ++ snapshotLayer ++ Clock.live ++ Blocking.live ++ zio.console.Console.live)
}
