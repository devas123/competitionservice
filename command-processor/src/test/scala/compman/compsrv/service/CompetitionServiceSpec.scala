package compman.compsrv.service

import akka.kafka.{ProducerMessage, ProducerSettings}
import akka.kafka.testkit.ProducerResultFactory
import akka.stream.scaladsl.Flow
import akka.NotUsed
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{KafkaSupervisorCommand, PublishMessage, QuerySync}
import compman.compsrv.logic.actors._
import compman.compsrv.model.extensions.InstantOps
import compman.compsrv.SpecBase
import compman.compsrv.logic.actor.kafka.persistence.KafkaBasedEventSourcedBehavior.{CommandReceived, KafkaProducerFlow}
import compservice.model.protobuf.command.{Command, CommandType}
import compservice.model.protobuf.commandpayload.CreateCompetitionPayload
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.model._
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.DurationInt

class CompetitionServiceSpec extends SpecBase {
  object Deps {
    val competitionId   = "test-competition-id"
    val groupId: String = UUID.randomUUID().toString
    val clientId        = "client"
  }

  import Deps._

  test("The Competition Processor should accept commands") {
    val kafkaSupervisor = actorTestKit.createTestProbe[KafkaSupervisorCommand]()
    val mockedKafkaProducerFlow: KafkaProducerFlow =
      Flow[ProducerMessage.Envelope[String, Array[Byte], NotUsed]].map {
        case msg: ProducerMessage.MultiMessage[String, Array[Byte], NotUsed] =>
          msg.records.foreach(rec => kafkaSupervisor.ref ! PublishMessage(rec.topic(), rec.key(), rec.value()))
          ProducerResultFactory.multiResult(msg)
        case other => throw new Exception(s"excluded: $other")
      }
    val producerSettings = ProducerSettings.create(actorTestKit.system, new StringSerializer, new ByteArraySerializer)

    val processor = actorTestKit.spawn(
      CompetitionProcessorActorV2.behavior(
        competitionId,
        "test-events",
        "test-commands-callback",
        "test-notifications",
        producerSettings,
        kafkaSupervisor.ref,
        SnapshotService.test,
        Some(mockedKafkaProducerFlow)
      ),
      s"CompetitionProcessor-$competitionId"
    )
    val unwrapped = kafkaSupervisor.expectMessageType[QuerySync](3.seconds)
    val promise   = unwrapped.promise
    promise.success(Seq.empty)
    val command = Command().withMessageInfo(
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
    processor ! CommandReceived("topic", "competitionId", command, 10)
    val notification = kafkaSupervisor.expectMessageType[PublishMessage](3.seconds)
    val eventBytes   = kafkaSupervisor.expectMessageType[PublishMessage](3.seconds)
    val event        = Event.parseFrom(eventBytes.message)
    assert(notification != null)
    assert(event.`type` == EventType.COMPETITION_CREATED)
    assert(event.messageInfo.flatMap(_.payload.competitionCreatedPayload).isDefined)
    assert(event.messageInfo.map(_.correlationId).contains(command.messageInfo.map(_.id).get))
    assert(event.localEventNumber.toLong == 0L)
  }

}
