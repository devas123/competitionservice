package compman.compsrv.logic.actors

import compman.compsrv.config.CommandProcessorConfig
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{CreateTopicIfMissing, KafkaSupervisorCommand, PublishMessage, QuerySync}
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.CompetitionProcessorSupervisorActor.CommandReceived
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.model.commands.payload.CreateCompetitionPayload
import compman.compsrv.model.commands.{CommandDTO, CommandType}
import compman.compsrv.model.dto.competition.{CompetitionPropertiesDTO, CompetitionStatus, RegistrationInfoDTO}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.durationInt
import zio.kafka.consumer.ConsumerSettings
import zio.kafka.producer.ProducerSettings
import zio.logging.Logging
import zio.test._
import zio.{Layer, Ref}

import java.time.Instant
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

    val producerSettings: ProducerSettings = ProducerSettings(brokers)
    val loggingLayer: Layer[Nothing, Logging] = CompetitionLogging.Live.loggingLayer
    val snapshotLayer: Layer[Nothing, SnapshotService.Snapshot] = SnapshotService.test.toLayer
  }

  import Deps._
  import zio.test.environment._

  def spec: Spec[TestEnvironment, TestFailure[Throwable], TestSuccess] =
    suite("The Competition Processor should")(testM("Accept commands") {
      ActorSystem("Test").use { actorSystem =>
        for {
          snapshotsRef <- Ref.make(Map.empty[String, CompetitionState])
          kafkaSupervisor <- TestKit[KafkaSupervisorCommand](actorSystem)
          processor <- actorSystem.make(
            s"CompetitionProcessorSupervisor",
            ActorConfig(),
            (),
            CompetitionProcessorSupervisorActor.behavior(
              CommandProcessorOperationsFactory.test(snapshotsRef),
              CommandProcessorConfig(None, "events", "notif"),
              kafkaSupervisor.ref
            )
          )
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
          _ <- processor ! CommandReceived(competitionId, command)
          _ <- kafkaSupervisor.expectMessageClass(3.seconds, classOf[CreateTopicIfMissing])
          msg <- kafkaSupervisor.expectMessageClass(3.seconds, classOf[QuerySync])
          unwrapped = msg.get
          promise = unwrapped.promise
          _ <- promise.succeed(Seq.empty)
          eventsOptional <- kafkaSupervisor.expectMessageClass(3.seconds, classOf[PublishMessage])
          event = eventsOptional.get.message
        } yield assertTrue(event != null)
      }
    }).provideLayer(loggingLayer ++ snapshotLayer ++ Clock.live ++ Blocking.live ++ zio.console.Console.live)
}
