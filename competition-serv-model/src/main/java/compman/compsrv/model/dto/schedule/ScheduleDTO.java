package compman.compsrv.model.dto.schedule;

import compman.compsrv.model.dto.dashboard.MatDescriptionDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@Accessors(chain = true)
@NoArgsConstructor
public class ScheduleDTO {
    private String id;
    private PeriodDTO[] periods;
    private MatDescriptionDTO[] mats;
}
