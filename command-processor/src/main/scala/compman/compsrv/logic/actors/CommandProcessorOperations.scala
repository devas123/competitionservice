package compman.compsrv.logic.actors

import compman.compsrv.logic.CompetitionState
import compman.compsrv.model.Payload
import compman.compsrv.model.commands.payload.CreateCompetitionPayload
import compman.compsrv.model.dto.competition._
import compman.compsrv.model.dto.schedule.ScheduleDTO
import zio.{Has, Ref, RIO, URIO, ZIO}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.kafka.producer.Producer
import zio.logging.Logging

import java.time.Instant
import scala.jdk.CollectionConverters._

trait CommandProcessorOperations[-E] {
  def getStateSnapshot(id: String): URIO[E with SnapshotService.Snapshot, Option[CompetitionState]]

  def saveStateSnapshot(state: CompetitionState): URIO[E with SnapshotService.Snapshot, Unit]

  def createInitialState(competitionId: String, payload: Option[Payload]): CompetitionState = {
    val defaultProperties = Option(
      new CompetitionPropertiesDTO().setId(competitionId).setStatus(CompetitionStatus.CREATED)
        .setCreationTimestamp(Instant.now()).setBracketsPublished(false).setSchedulePublished(false)
        .setStaffIds(Array.empty).setEmailNotificationsEnabled(false).setTimeZone("UTC")
    )
    val defaultRegInfo = Some(
      new RegistrationInfoDTO().setId(competitionId).setRegistrationGroups(Map.empty[String, RegistrationGroupDTO].asJava)
        .setRegistrationPeriods(Map.empty[String, RegistrationPeriodDTO].asJava).setRegistrationOpen(false)
    )

    CompetitionState(
      id = competitionId,
      competitors = Option(Map.empty),
      competitionProperties = payload.flatMap {
        case ccp: CreateCompetitionPayload => Option(ccp.getProperties)
        case _                             => defaultProperties
      }.orElse(defaultProperties).map(_.setId(competitionId).setTimeZone("UTC"))
        .map(_.setStatus(CompetitionStatus.CREATED))
        .map(pr => if (pr.getStartDate == null) pr.setStartDate(Instant.now()) else pr)
        .map(pr => if (pr.getCreationTimestamp == null) pr.setCreationTimestamp(Instant.now()) else pr),
      stages = Some(Map.empty),
      fights = Some(Map.empty),
      categories = Some(Map.empty),
      registrationInfo = payload.flatMap {
        case p: CreateCompetitionPayload => Option(p.getReginfo)
        case _                           => defaultRegInfo
      }.orElse(defaultRegInfo).map(_.setId(competitionId)),
      schedule = Some(new ScheduleDTO().setId(competitionId).setMats(Array.empty).setPeriods(Array.empty)),
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

      override def createInitialState(competitionId: String, payload: Option[Payload]): CompetitionState = {
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
