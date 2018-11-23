package compman.compsrv.kafka.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import compman.compsrv.json.ObjectMapperFactory;
import compman.compsrv.model.es.events.EventHolder;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

public class EventSerializer implements Serializer<EventHolder> {
    private final ObjectMapper objectMapper = ObjectMapperFactory.INSTANCE.createObjectMapper();
    /**
     * Default constructor needed by Kafka
     */
    public EventSerializer() {

    }

    @Override
    public void configure(Map<String, ?> config, boolean isKey) {
    }

    @Override
    public byte[] serialize(String topic, EventHolder data) {
        if (data == null)
            return null;
        try {
            return objectMapper.writeValueAsBytes(data);
        } catch (Exception e) {
            throw new SerializationException("Error serializing JSON message", e);
        }
    }

    @Override
    public void close() {
    }

}
