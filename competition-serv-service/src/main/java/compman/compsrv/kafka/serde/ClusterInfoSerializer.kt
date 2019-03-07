package compman.compsrv.kafka.serde

import compman.compsrv.cluster.ClusterInfo
import compman.compsrv.json.ObjectMapperFactory
import org.apache.kafka.common.serialization.Serializer

class ClusterInfoSerializer : Serializer<ClusterInfo> {
    private val objectMapper = ObjectMapperFactory.createObjectMapper()

    override fun configure(configs: MutableMap<String, *>?, isKey: Boolean) {
    }

    override fun serialize(topic: String?, data: ClusterInfo?): ByteArray {
        return objectMapper.writeValueAsBytes(data)
    }

    override fun close() {
    }

}