package compman.compsrv.kafka.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import compman.compsrv.json.ObjectMapperFactory;
import compman.compsrv.model.es.commands.Command;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

public class CommandSerializer implements Serializer<Command> {
    private final ObjectMapper objectMapper = ObjectMapperFactory.INSTANCE.createObjectMapper();
    /**
     * Default constructor needed by Kafka
     */
    public CommandSerializer() {

    }

    @Override
    public void configure(Map<String, ?> config, boolean isKey) {
    }

    @Override
    public byte[] serialize(String topic, Command data) {
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
