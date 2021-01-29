package compman.compsrv.json

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.TreeNode
import compman.compsrv.model.events.MessageInfo

interface MessageInfoExtensions {
    fun MessageInfo.fillProperties(
        p: JsonParser,
        node: TreeNode
    ) {
        this.categoryId = p.codec.treeToValue(node.get("categoryId"), String::class.java)
        this.competitionId = p.codec.treeToValue(node.get("competitionId"), String::class.java)
        this.matId = p.codec.treeToValue(node.get("matId"), String::class.java)
        this.competitorId = p.codec.treeToValue(node.get("competitorId"), String::class.java)
        this.correlationId = p.codec.treeToValue(node.get("correlationId"), String::class.java)
        this.id = p.codec.treeToValue(node.get("id"), String::class.java)
        this.metadata = p.codec.treeToValue(node.get("metadata"), LinkedHashMap::class.java) as? Map<String, String>
    }

}