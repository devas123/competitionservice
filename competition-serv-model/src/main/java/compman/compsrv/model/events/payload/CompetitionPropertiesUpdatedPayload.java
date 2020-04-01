package compman.compsrv.model.events.payload;

import compman.compsrv.model.commands.payload.Payload;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@NoArgsConstructor
public class CompetitionPropertiesUpdatedPayload implements Serializable, Payload {
    private Map<String, Object> properties;
}
