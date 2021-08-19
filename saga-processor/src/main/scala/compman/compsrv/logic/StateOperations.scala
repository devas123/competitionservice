package compman.compsrv.logic

import compman.compsrv.model.{CompetitionState, CompetitionStateImpl}
import compman.compsrv.model.dto.competition.{CompetitionPropertiesDTO, CompetitionStatus, RegistrationInfoDTO}
import compman.compsrv.model.dto.schedule.ScheduleDTO
import zio.Task

import java.time.Instant

object StateOperations {
  trait GetStateConfig {
    def eventTopic: String
    def id: String
  }

  trait Service[F[+_]] {
    def getState(config: GetStateConfig): F[CompetitionState]
    def createConfig(getStateConfig: GetStateConfig): F[GetStateConfig]
  }

  object Service {
    def apply[F[+_]](implicit F: Service[F]): Service[F] = F

    val live: Service[Task] =
      new StateOperations.Service[Task] {
        override def getState(config: StateOperations.GetStateConfig): Task[CompetitionState] =
          Task(
            CompetitionStateImpl(
              id = config.id,
              competitors = Option(Seq.empty),
              competitionProperties = Option(
                new CompetitionPropertiesDTO()
                  .setId(config.id)
                  .setStatus(CompetitionStatus.CREATED)
                  .setCreationTimestamp(Instant.now())
                  .setBracketsPublished(false)
                  .setSchedulePublished(false)
                  .setStaffIds(Array.empty)
                  .setEmailNotificationsEnabled(false)
                  .setTimeZone("UTC")
              ),
              stages = Some(
                Map.empty
              ),
              fights = Some(Map.empty),
              categories = Some(Seq.empty),
              registrationInfo = Some(
                new RegistrationInfoDTO()
                  .setId(config.id)
                  .setRegistrationGroups(Array.empty)
                  .setRegistrationPeriods(Array.empty)
                  .setRegistrationOpen(false)
              ),
              schedule = Some(new ScheduleDTO()
              .setId(config.id)
              .setMats(Array.empty)
              .setPeriods(Array.empty)),
              revision = 0L
            )
          )
        override def createConfig(
            getStateConfig: GetStateConfig
        ): Task[StateOperations.GetStateConfig] = Task.effectTotal(getStateConfig)
      }

  }

  def getLatestState[F[+_]: Service](config: GetStateConfig): F[CompetitionState] = Service[F]
    .getState(config)
  def createConfig[F[+_]: Service](getStateConfig: GetStateConfig): F[GetStateConfig] = Service[F]
    .createConfig(getStateConfig)
}
