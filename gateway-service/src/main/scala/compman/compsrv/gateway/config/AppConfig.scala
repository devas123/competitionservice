package compman.compsrv.gateway.config

import com.typesafe.config.ConfigFactory
import zio._
import zio.config.magnolia.DeriveConfigDescriptor
import zio.config.read
import zio.config.typesafe.TypesafeConfigSource

final case class AppConfig(producer: ProducerConfig)

final case class ProducerConfig(bootstrapServers: String) {
  def brokers: List[String] = bootstrapServers.split(",").toList
}

object AppConfig {
  private val descriptor = DeriveConfigDescriptor.descriptor[AppConfig]

  def load(): Task[AppConfig] = for {
    rawConfig              <- ZIO.effect(ConfigFactory.load())
    configSource           <- ZIO.fromEither(TypesafeConfigSource.fromTypesafeConfig(rawConfig.getConfig("gateway")))
    config                 <- ZIO.fromEither(read(AppConfig.descriptor.from(configSource)))
  } yield config
}