package compman.compsrv.model.dto.schedule;

import compman.compsrv.model.dto.competition.FightDescriptionDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.ZonedDateTime;
import java.util.Date;

@Data
@AllArgsConstructor
@Accessors(chain = true)
@NoArgsConstructor
public class FightStartTimePairDTO {
    private FightDescriptionDTO fight;
    private Integer fightNumber;
    private ZonedDateTime startTime;
}