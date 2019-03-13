package compman.compsrv.model.dto.schedule;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@Accessors(chain = true)
@NoArgsConstructor
public class MatScheduleContainerDTO {
    private Integer currentFightNumber;
    private String id;
    private FightStartTimePairDTO[] fights;
    private String timeZone;
}
