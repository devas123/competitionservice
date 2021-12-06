package compman.compsrv.model.commands.payload;


import compman.compsrv.annotations.CommandPayload;
import compman.compsrv.annotations.EventPayload;
import compman.compsrv.model.Payload;
import compman.compsrv.model.commands.CommandType;
import compman.compsrv.model.dto.competition.CompScoreDTO;
import compman.compsrv.model.dto.competition.FightResultDTO;
import compman.compsrv.model.dto.competition.FightStatus;
import compman.compsrv.model.events.EventType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@NoArgsConstructor
@Accessors(chain = true)
@EventPayload(type = EventType.DASHBOARD_FIGHT_RESULT_SET)
@CommandPayload(type = CommandType.DASHBOARD_SET_FIGHT_RESULT_COMMAND)
public class SetFightResultPayload implements Serializable, Payload {
    private String fightId;
    private FightResultDTO fightResult;
    private CompScoreDTO[] scores;
    private FightStatus status;
}
