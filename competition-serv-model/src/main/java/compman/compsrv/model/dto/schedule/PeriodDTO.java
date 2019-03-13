package compman.compsrv.model.dto.schedule;

import compman.compsrv.model.dto.competition.CategoryDescriptorDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;

@Data
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class PeriodDTO {
    private String id;
    private String name;
    private ScheduleEntryDTO[] schedule;
    private CategoryDescriptorDTO[] categories;
    private Instant startTime;
    private Integer numberOfMats;
    private MatScheduleContainerDTO[] fightsByMats;
}
