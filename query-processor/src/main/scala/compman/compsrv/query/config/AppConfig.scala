package compman.compsrv.query.config

import com.typesafe.config.Config

final case class AppConfig(
  competitionEventListener: CompetitionEventListenerConfig,
  statelessEventListener: StatelessEventListenerConfig,
  consumer: ConsumerConfig
)

final case class ConsumerConfig(bootstrapServers: String, groupId: String) {
  def brokers: List[String] = bootstrapServers.split(",").toList
}

final case class MongodbConfig(
  host: String,
  port: Int,
  username: String,
  password: String,
  authenticationDb: String,
  queryDatabaseName: String
)

object MongodbConfig {
  def apply(config: Config): MongodbConfig = ???
}

final case class RoutingConfig(id: String, redirectUrl: String)

object AppConfig {
  def apply(config: Config): AppConfig = ???
  def load(config: Config): (AppConfig, MongodbConfig) = { (AppConfig(config), MongodbConfig(config)) }
}

case class CompetitionEventListenerConfig(competitionNotificationsTopic: String)
case class StatelessEventListenerConfig(academyNotificationsTopic: String, commandCallbackTopic: String)
