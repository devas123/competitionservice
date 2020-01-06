package compman.compsrv.model.events.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class DashboardFightOrderChange implements Serializable {
    private String fightId;
    private String newMatId;
    private Integer newOrderOnMat;
    private Instant newStartTime;
}
