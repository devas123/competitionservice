package compman.compsrv.model.events.payload;

import compman.compsrv.annotations.EventPayload;
import compman.compsrv.model.Payload;
import compman.compsrv.model.events.EventType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@EventPayload(type = EventType.DASHBOARD_FIGHT_ORDER_CHANGED)
public class DashboardFightOrderChangedPayload implements Serializable, Payload {
    private String fightId;
    private String currentMatId;
    private String newMatId;
    private Integer currentOrderOnMat;
    private Integer newOrderOnMat;
    private String periodId;
    private BigDecimal fightDuration;
}
