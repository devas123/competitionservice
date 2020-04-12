package compman.compsrv.model.dto.dashboard;

import compman.compsrv.model.dto.schedule.FightStartTimePairDTO;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@Getter
@Setter
public class MatDescriptionDTO {
    private String id;
    private String name;
    private String periodId;
    private Integer matOrder;
    private Integer numberOfFights;
    private FightStartTimePairDTO[] fightStartTimes;
}
