package compman.compsrv.model.events.payload;

import compman.compsrv.annotations.EventPayload;
import compman.compsrv.model.Payload;
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO;
import compman.compsrv.model.events.EventType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@EventPayload(type = EventType.FIGHT_PROPERTIES_UPDATED)
public class FightPropertiesUpdatedPayload implements Serializable, Payload {
    private String fightId;
    private Integer numberOnMat;
    private Instant startTime;
    private MatDescriptionDTO mat;
}
