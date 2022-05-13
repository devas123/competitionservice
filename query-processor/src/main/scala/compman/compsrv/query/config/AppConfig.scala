package compman.compsrv.query.config

import com.typesafe.config.ConfigFactory
import zio._
import zio.config.magnolia.DeriveConfigDescriptor
import zio.config.read
import zio.config.typesafe.TypesafeConfigSource

final case class AppConfig(competitionEventListener: CompetitionEventListenerConfig, statelessEventListener: StatelessEventListenerConfig, consumer: ConsumerConfig)

final case class ConsumerConfig(bootstrapServers: String, groupId: String) {
  def brokers: List[String] = bootstrapServers.split(",").toList
}

final case class MongodbConfig(host: String, port: Int, username: String, password: String, authenticationDb: String, queryDatabaseName: String)
object MongodbConfig {
  private[config] val descriptor = DeriveConfigDescriptor.descriptor[MongodbConfig]
}

final case class RoutingConfig(id: String, redirectUrl: String)

object AppConfig {
  private val descriptor = DeriveConfigDescriptor.descriptor[AppConfig]

  def load(): Task[(AppConfig, MongodbConfig)] = {
    val rawConfig              = ZIO.effect(ConfigFactory.load())
    for {
      appConfigSource <- ZIO(TypesafeConfigSource.fromTypesafeConfig(rawConfig.map(_.getConfig("processor"))))
      mongoConfigSource <- ZIO(TypesafeConfigSource.fromTypesafeConfig(rawConfig.map(_.getConfig("mongo"))))
      mongoContextConfig <- read(MongodbConfig.descriptor.from(mongoConfigSource))
      config <- read(AppConfig.descriptor.from(appConfigSource))
    } yield (config, mongoContextConfig)
  }
}

case class CompetitionEventListenerConfig(competitionNotificationsTopic: String)
case class StatelessEventListenerConfig(academyNotificationsTopic: String, commandCallbackTopic: String)
