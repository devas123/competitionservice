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
public class ScheduleEntryDTO {
    private String id;
    private String[] categoryIds;
    private String[] fightIds;
    private String matId;
    private String periodId;
    private String description;
    private ScheduleEntryType entryType;
    private String[] requirementIds;
    private Instant startTime;
    private Instant endTime;
    private Integer numberOfFights;
    private BigDecimal duration;
    private String[] invalidFightIds;
    private Integer order;
}
