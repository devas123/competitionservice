package compman.compsrv.kafka.serde;

import compman.compsrv.model.es.commands.CommandType;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

public class CommandTypeSerde implements Serde<CommandType> {
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {

    }

    @Override
    public void close() {

    }

    @Override
    public Serializer<CommandType> serializer() {
        return new CommandTypeSerializer();
    }

    @Override
    public Deserializer<CommandType> deserializer() {
        return new CommandTypeDeserializer();
    }
}
