package compman.compsrv.kafka.serde;

import compman.compsrv.model.es.commands.Command;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

public class CommandSerde implements Serde<Command> {
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {

    }

    @Override
    public void close() {

    }

    @Override
    public Serializer<Command> serializer() {
        return new CommandSerializer();
    }

    @Override
    public Deserializer<Command> deserializer() {
        return new CommandDeserializer();
    }
}
