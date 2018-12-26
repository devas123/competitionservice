package compman.compsrv.model.dto.schedule;

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
    private SchedulePropertiesDTO scheduleProperties;
    private PeriodDTO[] periods;
}
