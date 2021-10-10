package compman.compsrv.model.dto.competition;

import compman.compsrv.model.dto.schedule.ScheduleDTO;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class CompetitionStateDTO {
    private String id;
    private CategoryStateDTO[] categories;
    private CompetitionPropertiesDTO properties;
    private ScheduleDTO schedule;
}
