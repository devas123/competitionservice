package compman.compsrv.model.dto.competition;

import compman.compsrv.model.dto.schedule.DashboardPeriodDTO;
import lombok.Data;

@Data
public class CompetitionDashboardStateDTO {
    private String competitionId;
    private DashboardPeriodDTO[] periods;
}
