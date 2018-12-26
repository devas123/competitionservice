package compman.compsrv.model.dto.schedule;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@Accessors(chain = true)
@NoArgsConstructor
public class ScheduleEntryDTO {
    private String categoryId;
    private String startTime;
    private Integer numberOfFights;
    private BigDecimal fightDuration;
}
