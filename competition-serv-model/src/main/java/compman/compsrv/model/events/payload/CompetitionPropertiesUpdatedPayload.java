package compman.compsrv.model.events.payload;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@NoArgsConstructor
public class CompetitionPropertiesUpdatedPayload implements Serializable {
    private Map<String, Object> properties;
}
