package compman.compsrv.model.events.payload;

import compman.compsrv.annotations.EventPayload;
import compman.compsrv.model.Payload;
import compman.compsrv.model.events.EventType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@EventPayload(type = EventType.DASHBOARD_FIGHT_COMPETITORS_ASSIGNED)
public class FightCompetitorsAssignedPayload implements Serializable, Payload {
    private CompetitorAssignmentDescriptor[] assignments;
}
