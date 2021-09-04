package compman.compsrv.logic

import compman.compsrv.logic.StateOperations.GetStateConfig.defaultEventsTopic
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import zio.RIO

object StateOperations {
  trait GetStateConfig {
    def eventTopic: String = defaultEventsTopic(id)
    def id: String
  }

  object GetStateConfig {
    def defaultEventsTopic(id: String) = s"$id-events"
    def apply(competitionId: String): GetStateConfig = new GetStateConfig {
      override def id: String = competitionId
    }
  }

  trait Service[F[+_]] {
    def createConfig(getStateConfig: GetStateConfig): F[GetStateConfig]
  }

  object Service {
    def apply[F[+_]](implicit F: Service[F]): Service[F] = F

    val live: Service[LIO] = (getStateConfig: GetStateConfig) => RIO.effectTotal(getStateConfig)

  }

  def createConfig[F[+_]: Service](getStateConfig: GetStateConfig): F[GetStateConfig] = Service[F]
    .createConfig(getStateConfig)
}
