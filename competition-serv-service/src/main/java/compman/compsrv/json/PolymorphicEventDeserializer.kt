package compman.compsrv.json

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.TreeNode
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import compman.compsrv.model.Payloads
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import java.io.IOException


@Suppress("UNCHECKED_CAST")
class PolymorphicEventDeserializer : StdDeserializer<EventDTO>(EventDTO::class.java), MessageInfoExtensions {
    @Throws(IOException::class, JsonProcessingException::class)
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): EventDTO {
        val node: TreeNode = p.readValueAsTree()
        return  EventDTO().apply {
            fillProperties(p, node)
            val operation = p.codec.treeToValue(node.at("/type"), EventType::class.java)
            this.version = p.codec.treeToValue(node.get("/version"), Long::class.java)
            this.localEventNumber = p.codec.treeToValue(node.get("/localEventNumber"), Long::class.java)
            Payloads.getPayload(operation)?.let {
                payload = p.codec.treeToValue(node.at("/payload"), it)
            }
            this.type = operation
        }
    }
}
