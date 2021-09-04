package compman.compsrv.logic.actors

import compman.compsrv.jackson.SerdeApi
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.model.{CompetitionState, CompetitionStateImpl}
import compman.compsrv.model.dto.competition.{CompetitionPropertiesDTO, CompetitionStatus, RegistrationInfoDTO}
import compman.compsrv.model.dto.schedule.ScheduleDTO
import compman.compsrv.model.events.EventDTO
import org.apache.kafka.clients.producer.ProducerRecord
import zio.{Chunk, Has, Ref, RIO, ZLayer}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.kafka.consumer.{Consumer, Subscription}
import zio.kafka.producer.Producer
import zio.kafka.serde.Serde

import java.time.Instant

trait CommandProcessorOperations {
  def retrieveEvents(id: String): LIO[List[EventDTO]]
  def persistEvents(events: Seq[EventDTO]): LIO[Unit]
  def getLatestState(config: ActorConfig): LIO[CompetitionState] = RIO {
    CompetitionStateImpl(
      id = config.id,
      competitors = Option(Map.empty),
      competitionProperties = Option(
        new CompetitionPropertiesDTO().setId(config.id).setStatus(CompetitionStatus.CREATED)
          .setCreationTimestamp(Instant.now()).setBracketsPublished(false).setSchedulePublished(false)
          .setStaffIds(Array.empty).setEmailNotificationsEnabled(false).setTimeZone("UTC")
      ),
      stages = Some(Map.empty),
      fights = Some(Map.empty),
      categories = Some(Map.empty),
      registrationInfo = Some(
        new RegistrationInfoDTO().setId(config.id).setRegistrationGroups(Array.empty)
          .setRegistrationPeriods(Array.empty).setRegistrationOpen(false)
      ),
      schedule = Some(new ScheduleDTO().setId(config.id).setMats(Array.empty).setPeriods(Array.empty)),
      revision = 0L
    )
  }
}

object CommandProcessorOperations {
  def apply(
    consumerLayer: ZLayer[Clock with Blocking, Throwable, Has[Consumer.Service]],
    producerLayer: ZLayer[Any, Throwable, Has[Producer.Service[Any, String, EventDTO]]]
  ): CommandProcessorOperations = {

    new CommandProcessorOperations {
      override def retrieveEvents(id: String): LIO[List[EventDTO]] = Consumer.subscribeAnd(Subscription.topics(id))
        .plainStream(Serde.string, SerdeApi.eventDeserializer).runCollect.map(_.map(_.value).toList)
        .provideSomeLayer(consumerLayer).provideLayer(Clock.live ++ Blocking.live)
      override def persistEvents(events: Seq[EventDTO]): LIO[Unit] = {
        zio.kafka.producer.Producer.produceChunk[Any, String, EventDTO](Chunk.fromIterable(events).map(e =>
          new ProducerRecord[String, EventDTO](e.getCompetitionId, e)
        )).provideLayer(producerLayer ++ Blocking.live).ignore
      }
    }
  }
  def test(
    eventReceiver: Ref[Seq[EventDTO]],
    initialState: Option[CompetitionState] = None
  ): CommandProcessorOperations = {
    new CommandProcessorOperations {
      self =>
      override def retrieveEvents(id: String): LIO[List[EventDTO]] = RIO.effectTotal(List.empty)
      override def persistEvents(events: Seq[EventDTO]): LIO[Unit] = for {
        _ <- eventReceiver.update(evts => evts ++ events)
      } yield ()

      override def getLatestState(config: ActorConfig): LIO[CompetitionState] = {
        initialState.map(RIO.effectTotal(_)).getOrElse(super.getLatestState(config))
      }
    }
  }

}
