package compman.compsrv.logic

import compman.compsrv.logic.CommunicationApi.deserializer
import compman.compsrv.model.{CompetitionState, Errors, Payload}
import compman.compsrv.model.command.Commands
import compman.compsrv.model.command.Commands.Command
import zio.Task
import zio.kafka.consumer.{Consumer, Subscription}
import zio.kafka.serde.Serde

object StateOperations {
  trait GetStateConfig {
    def topic: String
    def id: String
  }

  trait Service[F[+_]] {
    def saveState(competitionState: CompetitionState): F[Either[Errors.Error, Unit]]
    def getState(config: GetStateConfig): F[Either[Errors.Error, CompetitionState]]
    def createConfig(command: Command[Payload]): F[GetStateConfig]
  }

  object Service {
    def apply[F[+_]](implicit F: Service[F]): Service[F] = F

    val live: Service[Task] = new StateOperations.Service[Task] {
      override def saveState(competitionState: CompetitionState): Task[Either[Errors.Error, Unit]] = ???

      override def getState(config: StateOperations.GetStateConfig): Task[Either[Errors.Error, CompetitionState]] = ???
      override def createConfig(command: Commands.Command[Payload]): Task[StateOperations.GetStateConfig] = ???
    }

  }

  def updateState[F[+_]: Service](competitionState: CompetitionState): F[Either[Errors.Error, Unit]] = Service[F].saveState(competitionState)
  def getLatestState[F[+_]: Service](config: GetStateConfig): F[Either[Errors.Error, CompetitionState]] = Service[F].getState(config)
  def createConfig[F[+_]: Service](command: Command[Payload]): F[GetStateConfig] = Service[F].createConfig(command)
}
