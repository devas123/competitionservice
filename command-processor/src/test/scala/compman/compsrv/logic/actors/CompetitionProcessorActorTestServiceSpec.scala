package compman.compsrv.logic.actors

import com.google.protobuf.timestamp.Timestamp
import com.google.protobuf.util.Timestamps
import compman.compsrv.config.CommandProcessorConfig
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{
  CreateTopicIfMissing,
  KafkaSupervisorCommand,
  PublishMessage,
  QuerySync
}
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.CompetitionProcessorSupervisorActor.CommandReceived
import compman.compsrv.logic.logging.CompetitionLogging
import compservice.model.protobuf.command.{Command, CommandType}
import compservice.model.protobuf.commandpayload.CreateCompetitionPayload
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.model.{CompetitionProperties, CompetitionStatus, RegistrationInfo}
import zio.{Layer, Ref}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.durationInt
import zio.kafka.consumer.ConsumerSettings
import zio.kafka.producer.ProducerSettings
import zio.logging.Logging
import zio.test._

import java.util.UUID

object CompetitionProcessorActorTestServiceSpec extends DefaultRunnableSpec {
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
          snapshotsRef    <- Ref.make(Map.empty[String, CompetitionState])
          kafkaSupervisor <- TestKit[KafkaSupervisorCommand](actorSystem)
          snapshotSaver   <- TestKit[SnapshotSaver.SnapshotSaverMessage](actorSystem)
          processor <- actorSystem.make(
            s"CompetitionProcessorSupervisor",
            ActorConfig(),
            (),
            CompetitionProcessorSupervisorActor.behavior(
              CommandProcessorOperations.test(snapshotsRef),
              CommandProcessorConfig(
                actorIdleTimeoutMillis = None,
                eventsTopicPrefix = "events",
                competitionNotificationsTopic = "competition-events",
                academyNotificationsTopic = "academy-events",
                commandCallbackTopic = "commands-callback"
              ),
              kafkaSupervisor.ref,
              snapshotSaver.ref
            )
          )
          command = {
            Command().withMessageInfo(
              MessageInfo().withId(UUID.randomUUID().toString).withCompetitionId(competitionId)
                .withPayload(MessageInfo.Payload.CreateCompetitionPayload(
                  CreateCompetitionPayload().withReginfo(
                    RegistrationInfo().withId(competitionId).withRegistrationGroups(Map.empty)
                      .withRegistrationPeriods(Map.empty).withRegistrationOpen(true)
                  ).withProperties(
                    CompetitionProperties().withId(competitionId).withCompetitionName("Test competition")
                      .withStatus(CompetitionStatus.CREATED).withTimeZone("UTC")
                      .withStartDate(Timestamp.fromJavaProto(Timestamps.fromMillis(System.currentTimeMillis())))
                      .withEndDate(Timestamp.fromJavaProto(Timestamps.fromMillis(System.currentTimeMillis())))
                  )
                ))
            ).withType(CommandType.CREATE_COMPETITION_COMMAND)

          }
          _   <- processor ! CommandReceived(competitionId, command)
          _   <- kafkaSupervisor.expectMessageClass(3.seconds, classOf[CreateTopicIfMissing])
          msg <- kafkaSupervisor.expectMessageClass(3.seconds, classOf[QuerySync])
          unwrapped = msg.get
          promise   = unwrapped.promise
          _              <- promise.succeed(Seq.empty)
          eventsOptional <- kafkaSupervisor.expectMessageClass(3.seconds, classOf[PublishMessage])
          event = eventsOptional.get.message
        } yield assertTrue(event != null)
      }
    }).provideLayer(loggingLayer ++ snapshotLayer ++ Clock.live ++ Blocking.live ++ zio.console.Console.live)
}
