package compman.compsrv.model.events.payload;

import compman.compsrv.model.dto.competition.CompetitionDashboardStateDTO;
import compman.compsrv.model.dto.competition.CompetitionPropertiesDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DashboardCreatedPayload implements Serializable {
    private CompetitionDashboardStateDTO dashboardState;
}
