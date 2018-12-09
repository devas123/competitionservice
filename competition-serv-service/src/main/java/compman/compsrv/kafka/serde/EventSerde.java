package compman.compsrv.kafka.serde;

import compman.compsrv.model.es.events.EventHolder;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

public class EventSerde implements Serde<EventHolder> {
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {

    }

    @Override
    public void close() {

    }

    @Override
    public Serializer<EventHolder> serializer() {
        return new EventSerializer();
    }

    @Override
    public Deserializer<EventHolder> deserializer() {
        return new EventDeserializer();
    }
}
