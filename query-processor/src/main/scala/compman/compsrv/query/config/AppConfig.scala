package compman.compsrv.query.config

import com.typesafe.config.ConfigFactory
import zio._
import zio.config.magnolia.DeriveConfigDescriptor
import zio.config.read
import zio.config.typesafe.TypesafeConfigSource

final case class AppConfig(competitionEventListener: CompetitionEventListenerConfig, consumer: ConsumerConfig)

final case class ConsumerConfig(bootstrapServers: String, groupId: String) {
  def brokers: List[String] = bootstrapServers.split(",").toList
}

final case class MongodbConfig(host: String, port: String, username: String, password: String, authenticationDb: String)
object MongodbConfig {
  private[config] val descriptor = DeriveConfigDescriptor.descriptor[MongodbConfig]
}

final case class RoutingConfig(id: String, redirectUrl: String)

object AppConfig {
  private val descriptor = DeriveConfigDescriptor.descriptor[AppConfig]

  def load(): Task[(AppConfig, MongodbConfig)] = for {
    rawConfig              <- ZIO.effect(ConfigFactory.load())
    appConfigSource           <- ZIO.fromEither(TypesafeConfigSource.fromTypesafeConfig(rawConfig.getConfig("processor")))
    mongoConfigSource           <- ZIO.fromEither(TypesafeConfigSource.fromTypesafeConfig(rawConfig.getConfig("mongo")))
    mongoContextConfig <- ZIO.fromEither(read(MongodbConfig.descriptor.from(mongoConfigSource)))
    config                 <- ZIO.fromEither(read(AppConfig.descriptor.from(appConfigSource)))
  } yield (config, mongoContextConfig)
}

case class CompetitionEventListenerConfig(competitionNotificationsTopic: String)
