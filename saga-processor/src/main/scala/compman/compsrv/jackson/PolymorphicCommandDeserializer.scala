package compman.compsrv.jackson

import com.fasterxml.jackson.core.{JsonParser, TreeNode}
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import compman.compsrv.model.Payloads
import compman.compsrv.model.commands.{CommandDTO, CommandType}

class PolymorphicCommandDeserializer
    extends StdDeserializer[CommandDTO](classOf[CommandDTO])
    with MessageInfoOperations {
  override def deserialize(p: JsonParser, ctxt: DeserializationContext): CommandDTO = {
    val node: TreeNode = p.readValueAsTree()
    val command        = fillInfo(new CommandDTO(), p, node)
    val operation      = p.getCodec.treeToValue(node.at("/type"), classOf[CommandType])
    setPayload(command, p, node, Option(Payloads.getPayload(operation))).setType(operation)
  }
}
