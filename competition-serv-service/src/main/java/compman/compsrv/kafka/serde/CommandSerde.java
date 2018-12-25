package compman.compsrv.kafka.serde;

import compman.compsrv.model.commands.CommandDTO;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

public class CommandSerde implements Serde<CommandDTO> {
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {

    }

    @Override
    public void close() {

    }

    @Override
    public Serializer<CommandDTO> serializer() {
        return new CommandSerializer();
    }

    @Override
    public Deserializer<CommandDTO> deserializer() {
        return new CommandDeserializer();
    }
}
