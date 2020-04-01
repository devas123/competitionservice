package compman.compsrv.model.dto.schedule;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@AllArgsConstructor
@Accessors(chain = true)
@NoArgsConstructor
public class ScheduleRequirementDTO {
    private String id;
    private String[] categoryIds;
    private String[] fightIds;
    private String matId;
    private String periodId;
    private String name;
    private String color;
    private ScheduleRequirementType entryType;
    private boolean force;
    private Instant startTime;
    private Instant endTime;
    private BigDecimal durationMinutes;
    private Integer entryOrder;
}
