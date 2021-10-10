package compman.compsrv.jackson

import com.fasterxml.jackson.core.{JsonParser, TreeNode}
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.Payloads

class PolymorphicEventDeserializer
    extends StdDeserializer[EventDTO](classOf[EventDTO])
    with MessageInfoOperations {
  override def deserialize(p: JsonParser, ctxt: DeserializationContext): EventDTO = {
    val node: TreeNode = p.readValueAsTree()
    val event          = fillInfo(new EventDTO(), p, node)
    val operation      = p.getCodec.treeToValue(node.at("/type"), classOf[EventType])
    event.setVersion(p.getCodec.treeToValue(node.get("/version"), classOf[Long]))
    setPayload(event, p, node, Option(Payloads.getPayload(operation))).setType(operation)
  }
}
