package compman.compsrv.gateway.config

import com.typesafe.config.Config

import scala.jdk.CollectionConverters._

final case class AppConfig(
  producer: ProducerConfig,
  consumer: ConsumerConfig,
  callbackTimeoutMs: Int,
  proxy: ProxyConfig
)

final case class ConsumerConfig(
  callbackTopic: String,
  eventsTopicPrefix: String,
  groupId: String,
  academyNotificationsTopic: String
)

final case class ProxyLocation(host: String, port: Int, protocol: String = "http") {
  def toProxyPass = s"$protocol://$host:$port"
}
final case class ProxyConfig(locations: Map[String, ProxyLocation])

object ProxyConfig {
  def load(config: Config): ProxyConfig = {
    val locations = config.getConfig("locations")
    val proxyLocations = locations.entrySet().asScala.map { e =>
      val proxyLocationConfig = locations.getConfig(e.getKey)
      e.getKey -> ProxyLocation(proxyLocationConfig.getString("host"), proxyLocationConfig.getInt("port"))
    }.toMap
    ProxyConfig(proxyLocations)
  }

}

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
    callbackTimeoutMs = config.getInt("gateway.callbackTimeoutMs"),
    proxy = ProxyConfig.load(config.getConfig("proxy"))
  )
}
