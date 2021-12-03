package compman.compsrv.query.serde

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
      case compman.compsrv.model.NotificationTypes.ProcessingStarted => p.getCodec
          .treeToValue(node, classOf[CompetitionProcessingStarted])
      case compman.compsrv.model.NotificationTypes.ProcessingStopped => p.getCodec
          .treeToValue(node, classOf[CompetitionProcessingStopped])
      case _ => throw new RuntimeException("Unknown notification type")
    }
  }
}
