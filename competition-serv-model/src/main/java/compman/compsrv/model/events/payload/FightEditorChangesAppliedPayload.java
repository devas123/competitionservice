package compman.compsrv.model.events.payload;

import compman.compsrv.model.commands.payload.Payload;
import compman.compsrv.model.dto.competition.CompScoreDTO;
import compman.compsrv.model.dto.competition.FightDescriptionDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class FightEditorChangesAppliedPayload implements Serializable, Payload {
    private FightDescriptionDTO[] updates;
    private FightDescriptionDTO[] newFights;
    private String[] removedFighids;
}
