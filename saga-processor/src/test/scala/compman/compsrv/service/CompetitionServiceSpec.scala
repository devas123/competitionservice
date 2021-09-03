package compman.compsrv.service

import compman.compsrv.jackson.SerdeApi.eventSerialized
import compman.compsrv.logic.StateOperations.GetStateConfig
import compman.compsrv.logic.actors.{CommandProcessorOperations, CompetitionProcessor, CompetitionProcessorActorRef}
import compman.compsrv.logic.actors.Messages.ProcessCommand
import compman.compsrv.model.commands.{CommandDTO, CommandType}
import compman.compsrv.model.commands.payload.CreateCompetitionPayload
import compman.compsrv.model.dto.competition.{CompetitionPropertiesDTO, CompetitionStatus, RegistrationInfoDTO}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.service.Deps._
import zio.{Has, Ref, Task, ZLayer}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.durationInt
import zio.kafka.consumer.{Consumer, ConsumerSettings}
import zio.kafka.producer.{Producer, ProducerSettings}
import zio.kafka.serde.Serde
import zio.test._
import zio.test.Assertion._

import java.time.Instant
import java.util.UUID

object Deps {
  val competitionId   = "test-competition-id"
  val groupId: String = UUID.randomUUID().toString
  val clientId        = "client"
  private val brokers = List("localhost:9092")
  val consumerSettings: ConsumerSettings = ConsumerSettings(brokers).withGroupId(groupId).withClientId(clientId)
    .withCloseTimeout(30.seconds).withPollTimeout(10.millis).withProperty("enable.auto.commit", "false")
    .withProperty("auto.offset.reset", "earliest")

  val producerSettings: ProducerSettings = ProducerSettings(brokers)
  val consumerLayer: ZLayer[Clock with Blocking, Throwable, Has[Consumer.Service]] = Consumer.make(consumerSettings)
    .toLayer
  val producerLayer: ZLayer[Any, Throwable, Has[Producer.Service[Any, String, EventDTO]]] = Producer
    .make[Any, String, EventDTO](producerSettings, Serde.string, eventSerialized).toLayer
  val layers: ZLayer[Clock with Blocking with Any, Throwable, Has[Consumer.Service] with Has[
    Producer.Service[Any, String, EventDTO]
  ]] = consumerLayer ++ producerLayer
}

object CompetitionServiceSpec extends DefaultRunnableSpec {
  import zio.test.environment._
  def spec: Spec[TestEnvironment, TestFailure[Throwable], TestSuccess] =
    suite("The Competition Processor should")(testM("Accept commands") {
      for {
        actorsRef <- Ref.make(Map.empty[String, CompetitionProcessorActorRef])
        eventsRef <- Ref.make(Seq.empty[EventDTO])
        processor <- CompetitionProcessor(
          competitionId,
          GetStateConfig(competitionId),
          CommandProcessorOperations.test(testEnvironment, eventsRef),
          CompetitionProcessor.Context(actorsRef, competitionId)
        )(() => actorsRef.update(m => m - competitionId))
        _ <- actorsRef.update(m => m + (competitionId -> processor))
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

        _      <- processor ! ProcessCommand(command)
        _      <- Task { Thread.sleep(1000) }
        events <- eventsRef.get
        _      <- Task { println(events) }
      } yield assert(events)(isNonEmpty) && assert(events.head.getType)(equalTo(EventType.COMPETITION_CREATED))
    })
}
