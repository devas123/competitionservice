package compman.compsrv.logic

import compman.compsrv.model.CompetitionState
import compman.compsrv.repository.CompetitionStateCrudRepository
import zio.Task

object StateOperations {
  trait GetStateConfig {
    def topic: String
    def id: String
  }

  trait Service[F[+_]] {
    def saveState(competitionState: CompetitionState, db: CompetitionStateCrudRepository[F]): F[Unit]
    def getState(config: GetStateConfig, db: CompetitionStateCrudRepository[F]): F[CompetitionState]
    def createConfig(getStateConfig: GetStateConfig): F[GetStateConfig]
  }

  object Service {
    def apply[F[+_]](implicit F: Service[F]): Service[F] = F

    val live: Service[Task] = new StateOperations.Service[Task] {
      override def saveState(competitionState: CompetitionState, db: CompetitionStateCrudRepository[Task]): Task[Unit] = db.add(competitionState)

      override def getState(config: StateOperations.GetStateConfig, db: CompetitionStateCrudRepository[Task]): Task[CompetitionState] = db.get(config.id)
      override def createConfig(getStateConfig: GetStateConfig): Task[StateOperations.GetStateConfig] = Task.effectTotal(getStateConfig)
    }

  }

  def saveState[F[+_]: Service](competitionState: CompetitionState, db: CompetitionStateCrudRepository[F]): F[Unit] = Service[F].saveState(competitionState, db)
  def getLatestState[F[+_]: Service](config: GetStateConfig, db: CompetitionStateCrudRepository[F]): F[CompetitionState] = Service[F].getState(config, db)
  def createConfig[F[+_]: Service](getStateConfig: GetStateConfig): F[GetStateConfig] = Service[F].createConfig(getStateConfig)
}
