package compman.compsrv.query.serde

import com.fasterxml.jackson.core.{JsonParser, TreeNode}
import compman.compsrv.model.events.MessageInfo
import compman.compsrv.model.Payload

import java.util

trait MessageInfoOperations {
  def fillInfo[T <: MessageInfo](target: T, p: JsonParser, node: TreeNode): T = {
    target.setCategoryId(p.getCodec.treeToValue(node.get("categoryId"), classOf[String]))
    target.setCompetitionId(p.getCodec.treeToValue(node.get("competitionId"), classOf[String]))
    target.setMatId(p.getCodec.treeToValue(node.get("matId"), classOf[String]))
    target.setCompetitorId(p.getCodec.treeToValue(node.get("competitorId"), classOf[String]))
    target.setCorrelationId(p.getCodec.treeToValue(node.get("correlationId"), classOf[String]))
    target.setId(p.getCodec.treeToValue(node.get("id"), classOf[String]))
    target.setMetadata(p.getCodec.treeToValue(node.get("metadata"), classOf[util.LinkedHashMap[String, String]]))
    target
  }

  def setPayload[T <: MessageInfo](
    target: T,
    p: JsonParser,
    node: TreeNode,
    payloadClass: Option[Class[_ <: Payload]]
  ): T = {
    payloadClass.foreach(it => target.setPayload(p.getCodec.treeToValue(node.at("/payload"), it)))
    target
  }
}
