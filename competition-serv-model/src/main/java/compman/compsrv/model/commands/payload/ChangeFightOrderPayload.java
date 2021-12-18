package compman.compsrv.model.commands.payload;

import compman.compsrv.annotations.CommandPayload;
import compman.compsrv.annotations.EventPayload;
import compman.compsrv.model.Payload;
import compman.compsrv.model.commands.CommandType;
import compman.compsrv.model.events.EventType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@CommandPayload(type = CommandType.DASHBOARD_FIGHT_ORDER_CHANGE_COMMAND)
@EventPayload(type = EventType.FIGHT_ORDER_CHANGED)
public class ChangeFightOrderPayload implements Serializable, Payload {
    private String fightId;
    private String newMatId;
    private Integer newOrderOnMat;
    private String periodId;
}
