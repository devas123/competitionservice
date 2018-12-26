package compman.compsrv.model.events.payload;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class CompetitionPropertiesUpdatedPayload implements Serializable {
    private Map<String, Object> properties;
}
