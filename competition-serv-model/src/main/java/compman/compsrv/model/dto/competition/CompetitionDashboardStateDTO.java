package compman.compsrv.model.dto.competition;

import compman.compsrv.model.dto.schedule.PeriodDTO;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class CompetitionDashboardStateDTO {
    private String competitionId;
    private PeriodDTO[] periods;
}
