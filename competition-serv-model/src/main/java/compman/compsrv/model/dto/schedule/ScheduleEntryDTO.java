package compman.compsrv.model.dto.schedule;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;

@Data
@AllArgsConstructor
@Accessors(chain = true)
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ScheduleEntryDTO {
    @EqualsAndHashCode.Include
    private String id;
    private String[] categoryIds;
    private MatIdAndSomeId[] fightIds;
    private String periodId;
    private String description;
    private String name;
    private String color;
    private ScheduleEntryType entryType;
    private String[] requirementIds;
    private Instant startTime;
    private Instant endTime;
    private Integer numberOfFights;
    private int duration;
    private Integer order;
}
