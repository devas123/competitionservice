package compman.compsrv.kafka.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import compman.compsrv.json.ObjectMapperFactory;
import compman.compsrv.model.competition.CompetitionDashboardState;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

public class CompetitionDashboardStateSerializer implements Serializer<CompetitionDashboardState> {
    private final ObjectMapper objectMapper = ObjectMapperFactory.INSTANCE.createObjectMapper();

    /**
     * Default constructor needed by Kafka
     */
    public CompetitionDashboardStateSerializer() {
    }

    @Override
    public void configure(Map<String, ?> config, boolean isKey) {
    }

    @Override
    public byte[] serialize(String topic, CompetitionDashboardState data) {
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
