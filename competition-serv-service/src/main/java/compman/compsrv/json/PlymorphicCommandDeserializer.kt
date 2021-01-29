package compman.compsrv.json

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.TreeNode
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import java.io.IOException


@Suppress("UNCHECKED_CAST")
class PlymorphicCommandDeserializer : StdDeserializer<CommandDTO>(CommandDTO::class.java), MessageInfoExtensions {
    @Throws(IOException::class, JsonProcessingException::class)
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): CommandDTO {
        val node: TreeNode = p.readValueAsTree()
        return CommandDTO().apply {
            fillProperties(p, node)
            this.executed = p.codec.treeToValue(node.get("executed"), Boolean::class.java)
            val operation = p.codec.treeToValue(node.at("type"), CommandType::class.java)
            this.type = operation
            operation.payloadClass?.let {
                payload = p.codec.treeToValue(node.get("payload"), it)
            }
        }
    }

}
