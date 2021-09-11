package compman.compsrv.query.sede

import com.fasterxml.jackson.core.{JsonParser, TreeNode}
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import compman.compsrv.model.{CommandProcessorNotification, CompetitionProcessingStarted, CompetitionProcessingStopped}
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.NotificationTypes.NotificationType

class PolymorphicNotificationDeserializer extends StdDeserializer[CommandProcessorNotification](classOf[EventDTO]) {
  override def deserialize(p: JsonParser, ctxt: DeserializationContext): CommandProcessorNotification = {
    val node: TreeNode = p.readValueAsTree()
    val operation      = p.getCodec.treeToValue(node.at("/notificationType"), classOf[NotificationType])
    operation match {
      case compman.compsrv.model.NotificationTypes.ProcessingStarted =>
        val id    = p.getCodec.treeToValue(node.get("competitionId"), classOf[String])
        val topic = p.getCodec.treeToValue(node.get("eventTopic"), classOf[String])
        CompetitionProcessingStarted(id, topic)
      case compman.compsrv.model.NotificationTypes.ProcessingStopped =>
        val id = p.getCodec.treeToValue(node.get("competitionId"), classOf[String])
        CompetitionProcessingStopped(id)

    }
  }
}
