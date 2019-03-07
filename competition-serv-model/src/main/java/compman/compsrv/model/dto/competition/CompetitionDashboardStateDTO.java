package compman.compsrv.model.dto.competition;

import compman.compsrv.model.dto.schedule.DashboardPeriodDTO;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class CompetitionDashboardStateDTO {
    private String competitionId;
    private DashboardPeriodDTO[] periods;
}
