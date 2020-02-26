package compman.compsrv.model.dto.schedule;

import compman.compsrv.model.dto.dashboard.MatDescriptionDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class PeriodDTO {
    private String id;
    private String name;
    private ScheduleEntryDTO[] scheduleEntries;
    private ScheduleRequirementDTO[] scheduleRequirements;
    private Instant startTime;
    private Instant endTime;
    private Boolean isActive;
    private MatDescriptionDTO[] mats;
    private Integer timeBetweenFights;
    private BigDecimal riskPercent;
}
