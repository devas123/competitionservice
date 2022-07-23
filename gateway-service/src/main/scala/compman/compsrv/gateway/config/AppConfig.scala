package compman.compsrv.gateway.config

import com.typesafe.config.Config

final case class AppConfig(producer: ProducerConfig, consumer: ConsumerConfig, callbackTimeoutMs: Int)

final case class ConsumerConfig(
  callbackTopic: String,
  eventsTopicPrefix: String,
  groupId: String,
  academyNotificationsTopic: String
)

final case class ProducerConfig(bootstrapServers: String, globalCommandsTopic: String, academyCommandsTopic: String)
object AppConfig {

  def load(config: Config): AppConfig = AppConfig(
    producer = ProducerConfig(
      bootstrapServers = config.getString("gateway.producer.bootstrapServers"),
      globalCommandsTopic = config.getString("gateway.producer.globalCommandsTopic"),
      academyCommandsTopic = config.getString("gateway.producer.academyCommandsTopic")
    ),
    consumer = ConsumerConfig(
      callbackTopic = config.getString("gateway.consumer.callbackTopic"),
      eventsTopicPrefix = config.getString("gateway.consumer.eventsTopicPrefix"),
      groupId = config.getString("gateway.consumer.groupId"),
      academyNotificationsTopic = config.getString("gateway.consumer.academyNotificationsTopic")
    ),
    callbackTimeoutMs = config.getInt("gateway.callbackTimeoutMs")
  )
}
