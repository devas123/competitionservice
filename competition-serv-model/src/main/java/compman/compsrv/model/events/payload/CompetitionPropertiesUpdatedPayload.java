package compman.compsrv.model.events.payload;

import compman.compsrv.annotations.EventPayload;
import compman.compsrv.model.Payload;
import compman.compsrv.model.events.EventType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Map;

@Data
@NoArgsConstructor
@Accessors(chain = true)
@EventPayload(type = EventType.COMPETITION_PROPERTIES_UPDATED)
public class CompetitionPropertiesUpdatedPayload implements Serializable, Payload {
    private Map<String, String> properties;
}
