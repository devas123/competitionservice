package compman.compsrv.logic.actors

import compman.compsrv.jackson.SerdeApi
import compman.compsrv.logic.actors.CompetitionProcessor.LiveEnv
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.model.{CompetitionState, CompetitionStateImpl}
import compman.compsrv.model.dto.competition.{CompetitionPropertiesDTO, CompetitionStatus, RegistrationInfoDTO}
import compman.compsrv.model.dto.schedule.ScheduleDTO
import compman.compsrv.model.events.EventDTO
import org.apache.kafka.clients.producer.ProducerRecord
import zio.{Chunk, Ref, RIO, URIO}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.kafka.consumer.{Consumer, Subscription}
import zio.kafka.serde.Serde
import zio.logging.Logging

import java.time.Instant

trait CommandProcessorOperations[-E] {
  def retrieveEvents(id: String, offset: Long): RIO[E, List[EventDTO]]
  def persistEvents(events: Seq[EventDTO]): RIO[E, Unit]
  def getStateSnapshot(id: String): URIO[E with SnapshotService.Snapshot, Option[CompetitionState]]
  def saveStateSnapshot(state: CompetitionState): URIO[E with SnapshotService.Snapshot, Unit]
  def createInitialState(config: ActorConfig): LIO[CompetitionState] = RIO {
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
  def apply[E](): CommandProcessorOperations[E with LiveEnv] = {

    new CommandProcessorOperations[E with LiveEnv] {
      override def retrieveEvents(id: String, offset: Long): RIO[E with LiveEnv, List[EventDTO]] = Consumer
        .subscribeAnd(Subscription.topics(id))
        .plainStream(Serde.string, SerdeApi.eventDeserializer)
        .filter(_.offset.offset >= offset)
        .runCollect
        .map(_.map(_.value).toList)
      override def persistEvents(events: Seq[EventDTO]): RIO[E with LiveEnv, Unit] = {
        zio.kafka.producer.Producer.produceChunk[Any, String, EventDTO](Chunk.fromIterable(events).map(e =>
          new ProducerRecord[String, EventDTO](e.getCompetitionId, e)
        )).ignore
      }

      override def getStateSnapshot(id: String): URIO[E with LiveEnv, Option[CompetitionState]] = SnapshotService.load(id)

      override def saveStateSnapshot(state: CompetitionState): URIO[E with LiveEnv, Unit] = SnapshotService.save(state)
    }
  }

  def test[Env](
    eventReceiver: Ref[Seq[EventDTO]],
    stateSnapshots: Ref[Map[String, CompetitionState]],
    initialState: Option[CompetitionState] = None
  ): CommandProcessorOperations[Env with Clock with Blocking with Logging] = {
    new CommandProcessorOperations[Env with Clock with Blocking with Logging] {
      self =>
      override def retrieveEvents(id: String, offset: Long): RIO[Env with Clock with Blocking with Logging, List[EventDTO]] = RIO.effectTotal(List.empty)
      override def persistEvents(events: Seq[EventDTO]): RIO[Env with Clock with Blocking with Logging, Unit] = for {
        _ <- eventReceiver.update(evts => evts ++ events)
      } yield ()

      override def createInitialState(config: ActorConfig): LIO[CompetitionState] = {
        initialState.map(RIO.effectTotal(_)).getOrElse(super.createInitialState(config))
      }

      override def getStateSnapshot(id: String): URIO[Env with Clock with Blocking with Logging with SnapshotService.Snapshot, Option[CompetitionState]] =
        for { map <- stateSnapshots.get } yield map.get(id)

      override def saveStateSnapshot(state: CompetitionState): URIO[Env with Clock with Blocking with Logging with SnapshotService.Snapshot, Unit] = for {
        _ <- stateSnapshots.update(_ + (state.id -> state))
      } yield ()
    }
  }

}
