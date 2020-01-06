package compman.compsrv.model.events.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class DashboardFightOrderChangedPayload implements Serializable {
    private String periodId;
    private DashboardFightOrderChange[] changedFights;
}
