package compman.compsrv.logic.actors

import com.google.protobuf.timestamp.Timestamp
import com.google.protobuf.util.Timestamps
import compman.compsrv.logic.CompetitionState
import compservice.model.protobuf.commandpayload.CreateCompetitionPayload
import compservice.model.protobuf.model.{CompetitionProperties, CompetitionStatus, RegistrationInfo, Schedule}
import zio.{Has, Ref, RIO, URIO, ZIO}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.kafka.producer.Producer
import zio.logging.Logging

trait CommandProcessorOperations[-E] {
  def getStateSnapshot(id: String): URIO[E with SnapshotService.Snapshot, Option[CompetitionState]]

  def saveStateSnapshot(state: CompetitionState): URIO[E with SnapshotService.Snapshot, Unit]
private def now: Timestamp = Timestamp.fromJavaProto(Timestamps.fromMillis(System.currentTimeMillis()))
  def createInitialState(competitionId: String, payload: Option[Any]): CompetitionState = {
    val defaultProperties = Option(
      CompetitionProperties()
        .withId(competitionId)
        .withStatus(CompetitionStatus.CREATED)
        .withCreationTimestamp(now)
        .withBracketsPublished(false)
        .withSchedulePublished(false)
        .withStaffIds(Array.empty)
        .withEmailNotificationsEnabled(false)
        .withTimeZone("UTC")
    )
    val defaultRegInfo = Some(
      RegistrationInfo()
        .withId(competitionId)
        .withRegistrationGroups(Map.empty)
        .withRegistrationPeriods(Map.empty)
        .withRegistrationOpen(false)
    )

    CompetitionState(
      id = competitionId,
      competitors = Option(Map.empty),
      competitionProperties = payload.flatMap {
        case ccp: CreateCompetitionPayload => ccp.properties
        case _                             => defaultProperties
      }.orElse(defaultProperties).map(_.withId(competitionId)
        .withTimeZone("UTC"))
        .map(_.withStatus(CompetitionStatus.CREATED))
        .map(pr => if (pr.startDate.isEmpty) pr.withStartDate(now) else pr)
        .map(pr => if (pr.creationTimestamp.isEmpty) pr.withCreationTimestamp(now) else pr),
      stages = Some(Map.empty),
      fights = Some(Map.empty),
      categories = Some(Map.empty),
      registrationInfo = payload.flatMap {
        case p: CreateCompetitionPayload => Option(p.getReginfo)
        case _                           => defaultRegInfo
      }.orElse(defaultRegInfo).map(_.withId(competitionId)),
      schedule = Some(Schedule()
        .withId(competitionId)
        .withMats(Array.empty)
        .withPeriods(Array.empty)),
      revision = 0L
    )
  }
}

object CommandProcessorOperations {

  private type CommandProcLive = Logging
    with Clock with Blocking with SnapshotService.Snapshot with Has[Producer]

  def apply[E](): RIO[E with CommandProcLive, CommandProcessorOperations[E with CommandProcLive]] = {
    for {
      operations <- ZIO.effect {
        new CommandProcessorOperations[E with CommandProcLive] {
          override def getStateSnapshot(id: String): URIO[E with CommandProcLive, Option[CompetitionState]] =
            SnapshotService.load(id)

          override def saveStateSnapshot(state: CompetitionState): URIO[E with CommandProcLive, Unit] = SnapshotService
            .save(state)
        }
      }
    } yield operations
  }

  def test[Env](
                 stateSnapshots: Ref[Map[String, CompetitionState]],
                 initialState: Option[CompetitionState] = None
               ): CommandProcessorOperations[Env with Clock with Blocking with Logging] = {
    new CommandProcessorOperations[Env with Clock with Blocking with Logging] {
      self =>

      override def createInitialState(competitionId: String, payload: Option[Any]): CompetitionState = {
        initialState.getOrElse(super.createInitialState(competitionId, payload))
      }

      override def getStateSnapshot(
                                     id: String
                                   ): URIO[Env with Clock with Blocking with Logging with SnapshotService.Snapshot, Option[CompetitionState]] = for {
        map <- stateSnapshots.get
      } yield map.get(id)

      override def saveStateSnapshot(
        state: CompetitionState
      ): URIO[Env with Clock with Blocking with Logging with SnapshotService.Snapshot, Unit] = for {
        _ <- stateSnapshots.update(_ + (state.id -> state))
      } yield ()
    }
  }

}
