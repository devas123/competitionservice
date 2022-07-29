package compman.compsrv.logic.actors

import akka.kafka.{ProducerMessage, ProducerSettings}
import akka.kafka.testkit.ProducerResultFactory
import akka.stream.scaladsl.Flow
import akka.NotUsed
import com.google.protobuf.timestamp.Timestamp
import com.google.protobuf.util.Timestamps
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{KafkaSupervisorCommand, PublishMessage, QuerySync, Subscribe}
import compman.compsrv.SpecBase
import compman.compsrv.config.CommandProcessorConfig
import compman.compsrv.logic.actor.kafka.persistence.KafkaBasedEventSourcedBehavior.KafkaProducerFlow
import compman.compsrv.logic.actors.CompetitionProcessorSupervisorActor.CommandReceived
import compservice.model.protobuf.command.{Command, CommandType}
import compservice.model.protobuf.commandpayload.CreateCompetitionPayload
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.model.{CompetitionProperties, CompetitionStatus, RegistrationInfo}
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}

import java.util.UUID
import scala.concurrent.duration.DurationInt

class CompetitionProcessorActorTestServiceSpec extends SpecBase {
  object Deps {
    val competitionId   = "test-competition-id"
    val groupId: String = UUID.randomUUID().toString
    val clientId        = "client"
  }
  import Deps._
  test("test end to end") {

    val kafkaSupervisor = actorTestKit.createTestProbe[KafkaSupervisorCommand]()
    val mockedKafkaProducerFlow: KafkaProducerFlow = Flow[ProducerMessage.Envelope[String, Array[Byte], NotUsed]].map {
      case msg: ProducerMessage.MultiMessage[String, Array[Byte], NotUsed] =>
        msg.records.foreach(rec => kafkaSupervisor.ref ! PublishMessage(rec.topic(), rec.key(), rec.value()))
        ProducerResultFactory.multiResult(msg)
      case other => throw new Exception(s"excluded: $other")
    }

    val producerSettings = ProducerSettings.create(actorTestKit.system, new StringSerializer, new ByteArraySerializer)

    val processor = actorTestKit.spawn(
      CompetitionProcessorSupervisorActor.behavior(
        producerSettings,
        CommandProcessorConfig(
          actorIdleTimeoutMillis = Some(30000L),
          eventsTopicPrefix = "event",
          commandsTopic = "competition-commands",
          competitionNotificationsTopic = "notifications",
          academyNotificationsTopic = "academies",
          commandCallbackTopic = "commands-callback",
          snapshotDbPath = "snapshots",
          groupId = UUID.randomUUID().toString
        ),
        kafkaSupervisor.ref,
        _ => SnapshotService.test,
        Some(mockedKafkaProducerFlow)
      ),
      "CompetitionProcessorSupervisor"
    )
    val command = {
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
    processor ! CommandReceived(competitionId, command, 10, 11)
    kafkaSupervisor.expectMessageType[Subscribe](10.seconds)
    val unwrapped = kafkaSupervisor.expectMessageType[QuerySync](3.seconds)
    val promise   = unwrapped.promise
    promise.success(Seq.empty)
    val event = kafkaSupervisor.expectMessageType[PublishMessage](3.seconds)
    assert(event != null)
  }

}
