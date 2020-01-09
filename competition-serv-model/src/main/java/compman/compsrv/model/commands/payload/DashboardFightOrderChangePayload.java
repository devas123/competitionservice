package compman.compsrv.model.commands.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class DashboardFightOrderChangePayload implements Serializable {
    private String fightId;
    private String currentMatId;
    private String newMatId;
    private Integer currentOrderOnMat;
    private Integer newOrderOnMat;
    private String periodId;
}
