package compman.compsrv.model.events.payload;

import compman.compsrv.model.dto.competition.FightDescriptionDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FightEditorChangesAppliedPayload implements Serializable {
    private FightDescriptionDTO updatedSourceFight;
    private FightDescriptionDTO updatedTargetFight;

}
