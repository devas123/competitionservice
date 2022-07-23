package compman.compsrv.config

import com.typesafe.config.Config

final case class AppConfig(
  commandProcessor: CommandProcessorConfig,
  consumer: ConsumerConfig,
  producer: ProducerConfig,
  snapshotConfig: SnapshotConfig
)

final case class ConsumerConfig(bootstrapServers: String, groupId: String, commandTopics: CommandTopics) {
  def brokers: List[String] = bootstrapServers.split(",").toList
}

final case class CommandTopics(competition: String, academy: String)

final case class ProducerConfig(bootstrapServers: String) {
  def brokers: List[String] = bootstrapServers.split(",").toList
}

final case class SnapshotConfig(databasePath: String)

object AppConfig {

  def load(config: Config): AppConfig = {
    val commandProcessorConfig = CommandProcessorConfig(
      actorIdleTimeoutMillis =
        if (config.hasPath("processor.commandProcessor.actorIdleTimeoutMillis"))
          Some(config.getLong("processor.commandProcessor.actorIdleTimeoutMillis"))
        else None,
      eventsTopicPrefix = config.getString("processor.commandProcessor.eventsTopicPrefix"),
      competitionNotificationsTopic = config.getString("processor.commandProcessor.competitionNotificationsTopic"),
      academyNotificationsTopic = config.getString("processor.commandProcessor.academyNotificationsTopic"),
      commandCallbackTopic = config.getString("processor.commandProcessor.commandCallbackTopic"),
      commandsTopic = config.getString("processor.consumer.commandTopics.competition"),
      snapshotDbPath = config.getString("processor.snapshotConfig.databasePath"),
      groupId = config.getString("processor.consumer.groupId")
    )
    val consumer = ConsumerConfig(
      bootstrapServers = config.getString("processor.consumer.bootstrapServers"),
      groupId = config.getString("processor.consumer.groupId"),
      commandTopics = CommandTopics(
        competition = config.getString("processor.consumer.commandTopics.competition"),
        academy = config.getString("processor.consumer.commandTopics.academy")
      )
    )
    val producer       = ProducerConfig(bootstrapServers = config.getString("processor.producer.bootstrapServers"))
    val snapshotConfig = SnapshotConfig(databasePath = config.getString("processor.snapshotConfig.databasePath"))
    AppConfig(commandProcessorConfig, consumer, producer, snapshotConfig)
  }
}

case class CommandProcessorConfig(
  actorIdleTimeoutMillis: Option[Long],
  eventsTopicPrefix: String,
  commandsTopic: String,
  competitionNotificationsTopic: String,
  academyNotificationsTopic: String,
  commandCallbackTopic: String,
  snapshotDbPath: String,
  groupId: String
)
