package compman.compsrv.kafka.streams

import compman.compsrv.cluster.HostStoreInfo
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.state.StreamsMetadata
import org.slf4j.LoggerFactory
import javax.ws.rs.NotFoundException

/**
 * Looks up StreamsMetadata from KafkaStreams and converts the results
 * into Beans that can be JSON serialized.
 */
class MetadataService(private val streams: KafkaStreams) {

    companion object {
        private val log = LoggerFactory.getLogger(MetadataService::class.java)
    }
    /**
     * Get the metadata for all of the instances of this Kafka Streams application
     * @return List of [HostStoreInfo]
     */
    fun streamsMetadata(): List<HostStoreInfo> {
        // Get metadata for all of the instances of this Kafka Streams application
        val metadata = streams.allMetadata()
        return mapInstancesToHostStoreInfo(metadata)
    }

    /**
     * Get the metadata for all instances of this Kafka Streams application that currently
     * has the provided store.
     * @param store   The store to locate
     * @return  List of [HostStoreInfo]
     */
    fun streamsMetadataForStore(store: String): List<HostStoreInfo> {
        // Get metadata for all of the instances of this Kafka Streams application hosting the store
        val metadata = streams.allMetadataForStore(store)
        return mapInstancesToHostStoreInfo(metadata)
    }

    /**
     * Find the metadata for the instance of this Kafka Streams Application that has the given
     * store and would have the given key if it exists.
     * @param store   Store to find
     * @param key     The key to find
     * @return [HostStoreInfo]
     */
    fun <K> streamsMetadataForStoreAndKey(store: String,
                                          key: K,
                                          serializer: Serializer<K>): HostStoreInfo {
        // Get metadata for the instances of this Kafka Streams application hosting the store and
        // potentially the value for key
        log.info("Getting metadata for store $store and key $key")
        val m = streams.allMetadata()
        log.info("Metadata for this instance: $m")
        val metadata = streams.metadataForKey(store, key, serializer) ?: throw NotFoundException()
        return HostStoreInfo(metadata.host(),
                metadata.port(),
                metadata.stateStoreNames())
    }

    private fun mapInstancesToHostStoreInfo(
            metadatas: Collection<StreamsMetadata>): List<HostStoreInfo> {
        return metadatas.map { metadata ->
            HostStoreInfo(metadata.host(),
                    metadata.port(),
                    metadata.stateStoreNames())
        }
    }

}