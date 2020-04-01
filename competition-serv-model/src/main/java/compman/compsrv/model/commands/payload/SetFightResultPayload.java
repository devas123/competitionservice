package compman.compsrv.model.commands.payload;


import compman.compsrv.model.dto.competition.CompScoreDTO;
import compman.compsrv.model.dto.competition.FightResultDTO;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class SetFightResultPayload implements Serializable, Payload {
    private String fightId;
    private FightResultDTO fightResult;
    private CompScoreDTO[] scores;
}
