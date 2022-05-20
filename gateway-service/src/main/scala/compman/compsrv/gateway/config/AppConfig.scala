package compman.compsrv.gateway.config

import com.typesafe.config.ConfigFactory
import zio._
import zio.config.magnolia.DeriveConfigDescriptor
import zio.config.read
import zio.config.typesafe.TypesafeConfigSource

final case class AppConfig(producer: ProducerConfig, consumer: ConsumerConfig, callbackTimeoutMs: Int)

final case class ConsumerConfig(callbackTopic: String, eventsTopicPrefix: String, groupId: String, academyNotificationsTopic: String)

final case class ProducerConfig(bootstrapServers: String, globalCommandsTopic: String) {
  def brokers: List[String] = bootstrapServers.split(",").toList
}

object AppConfig {
  private val descriptor = DeriveConfigDescriptor.descriptor[AppConfig]

  def load(): Task[AppConfig] = for {
    configSource           <- ZIO(TypesafeConfigSource.fromTypesafeConfig(ZIO.effect(ConfigFactory.load()).map(_.getConfig("gateway"))))
    config                 <- read(AppConfig.descriptor.from(configSource))
  } yield config
}