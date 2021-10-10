package compman.compsrv.query.config

import com.typesafe.config.ConfigFactory
import io.getquill.CassandraContextConfig
import zio._
import zio.config.magnolia.DeriveConfigDescriptor
import zio.config.read
import zio.config.typesafe.TypesafeConfigSource

final case class AppConfig(competitionEventListener: CompetitionEventListenerConfig, consumer: ConsumerConfig)

final case class ConsumerConfig(bootstrapServers: String, groupId: String) {
  def brokers: List[String] = bootstrapServers.split(",").toList
}

final case class RoutingConfig(id: String, redirectUrl: String)

object AppConfig {
  private val descriptor = DeriveConfigDescriptor.descriptor[AppConfig]

  def load(): Task[(AppConfig, CassandraContextConfig)] = for {
    rawConfig              <- ZIO.effect(ConfigFactory.load())
    cassandraContextConfig <- ZIO.effect(CassandraContextConfig(rawConfig.getConfig("ctx")))
    configSource           <- ZIO.fromEither(TypesafeConfigSource.fromTypesafeConfig(rawConfig.getConfig("processor")))
    config                 <- ZIO.fromEither(read(AppConfig.descriptor.from(configSource)))
  } yield (config, cassandraContextConfig)
}

case class CompetitionEventListenerConfig(competitionNotificationsTopic: String)
