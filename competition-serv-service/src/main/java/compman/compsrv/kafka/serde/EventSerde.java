package compman.compsrv.kafka.serde;

import compman.compsrv.model.events.EventDTO;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

public class EventSerde implements Serde<EventDTO> {
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {

    }

    @Override
    public void close() {

    }

    @Override
    public Serializer<EventDTO> serializer() {
        return new EventSerializer();
    }

    @Override
    public Deserializer<EventDTO> deserializer() {
        return new EventDeserializer();
    }
}
