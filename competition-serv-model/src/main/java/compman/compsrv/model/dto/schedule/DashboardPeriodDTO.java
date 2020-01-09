package compman.compsrv.model.dto.schedule;

import compman.compsrv.model.dto.dashboard.MatDescriptionDTO;
import compman.compsrv.model.dto.dashboard.MatStateDTO;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class DashboardPeriodDTO {
    private String id;
    private String name;
    private MatDescriptionDTO[] mats;
    private Instant startTime;
    private Boolean isActive;
}
