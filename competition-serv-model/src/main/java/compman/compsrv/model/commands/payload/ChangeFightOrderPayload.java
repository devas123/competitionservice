package compman.compsrv.model.commands.payload;

import compman.compsrv.annotations.CommandPayload;
import compman.compsrv.model.Payload;
import compman.compsrv.model.commands.CommandType;
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
public class ChangeFightOrderPayload implements Serializable, Payload {
    private String fightId;
    private String currentMatId;
    private String newMatId;
    private Integer currentOrderOnMat;
    private Integer newOrderOnMat;
    private String periodId;
}
