package compman.compsrv.config

import zio._
import zio.config.read
import zio.config.magnolia.DeriveConfigDescriptor
import zio.config.typesafe.TypesafeConfigSource
import com.typesafe.config.ConfigFactory

final case class AppConfig(
                            commandProcessor: CommandProcessorConfig,
                            consumer: ConsumerConfig,
                            producer: ProducerConfig,
                            snapshotConfig: SnapshotConfig)


final case class ConsumerConfig(bootstrapServers: String, commandsTopic: String, groupId: String) {
  def brokers: List[String] = bootstrapServers.split(",").toList
}

final case class ProducerConfig(bootstrapServers: String) {
  def brokers: List[String] = bootstrapServers.split(",").toList
}

final case class SnapshotConfig(databasePath: String)

object AppConfig {
  private val descriptor = DeriveConfigDescriptor.descriptor[AppConfig]

  def load(): Task[AppConfig] =
    for {
      rawConfig    <- ZIO.effect(ConfigFactory.load().getConfig("processor"))
      configSource <- ZIO.fromEither(TypesafeConfigSource.fromTypesafeConfig(rawConfig))
      config       <- ZIO.fromEither(read(AppConfig.descriptor.from(configSource)))
    } yield config
}

case class CommandProcessorConfig(actorIdleTimeoutMillis: Option[Long], eventsTopicPrefix: String)
