package compman.compsrv.logic

import compman.compsrv.model.{CompetitionState, Payload}
import compman.compsrv.model.command.Commands
import compman.compsrv.model.command.Commands.Command
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
    def createConfig(command: Command[Payload]): F[GetStateConfig]
  }

  object Service {
    def apply[F[+_]](implicit F: Service[F]): Service[F] = F

    val live: Service[Task] = new StateOperations.Service[Task] {
      override def saveState(competitionState: CompetitionState, db: CompetitionStateCrudRepository[Task]): Task[Unit] = db.add(competitionState)

      override def getState(config: StateOperations.GetStateConfig, db: CompetitionStateCrudRepository[Task]): Task[CompetitionState] = db.get(config.id)
      override def createConfig(command: Commands.Command[Payload]): Task[StateOperations.GetStateConfig] = Task {
        new GetStateConfig {
          override def topic: String = s"events-${command.competitionId}"

          override def id: String = command.competitionId.get
        }
      }
    }

  }

  def updateState[F[+_]: Service](competitionState: CompetitionState, db: CompetitionStateCrudRepository[F]): F[Unit] = Service[F].saveState(competitionState, db)
  def getLatestState[F[+_]: Service](config: GetStateConfig, db: CompetitionStateCrudRepository[F]): F[CompetitionState] = Service[F].getState(config, db)
  def createConfig[F[+_]: Service](command: Command[Payload]): F[GetStateConfig] = Service[F].createConfig(command)
}
