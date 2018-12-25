package compman.compsrv.model.dto.competition;

import compman.compsrv.model.dto.schedule.ScheduleDTO;
import lombok.Data;

@Data
public class CompetitionStateDTO {
    private String competitionId;
    private CategoryStateDTO[] categories;
    private CompetitionPropertiesDTO properties;
    private ScheduleDTO schedule;
    private CompetitionDashboardStateDTO dashboardState;
    private CompetitionStatus status;
}
