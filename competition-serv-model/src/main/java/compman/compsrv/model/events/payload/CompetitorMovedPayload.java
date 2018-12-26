package compman.compsrv.model.events.payload;

import compman.compsrv.model.dto.competition.FightDescriptionDTO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class CompetitorMovedPayload implements Serializable {
    private FightDescriptionDTO updatedSourceFight;
    private FightDescriptionDTO updatedTargetFight;

}
