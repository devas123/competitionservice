package compman.compsrv.kafka.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import compman.compsrv.json.ObjectMapperFactory;
import compman.compsrv.model.competition.CompetitionDashboardState;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

import java.util.Map;

/**
 * JSON deserializer for Jackson's JsonNode tree model. Using the tree model allows it to work with arbitrarily
 * structured data without having associated Java classes. This deserializer also supports Connect schemas.
 */
public class CompetitionDashboardStateDeserializer implements Deserializer<CompetitionDashboardState> {
    private final ObjectMapper objectMapper = ObjectMapperFactory.INSTANCE.createObjectMapper();

    /**
     * Default constructor needed by Kafka
     */
    public CompetitionDashboardStateDeserializer() {
    }

    @Override
    public void configure(Map<String, ?> props, boolean isKey) {
    }

    @Override
    public CompetitionDashboardState deserialize(String topic, byte[] bytes) {
        if (bytes == null)
            return null;

        CompetitionDashboardState data;
        try {
            data = objectMapper.readValue(bytes, CompetitionDashboardState.class);
        } catch (Exception e) {
            throw new SerializationException(e);
        }

        return data;
    }

    @Override
    public void close() {

    }
}
