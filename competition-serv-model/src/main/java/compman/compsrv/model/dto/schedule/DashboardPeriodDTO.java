package compman.compsrv.model.dto.schedule;

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
    private String[] matIds;
    private Instant startTime;
    private Boolean isActive;
}
