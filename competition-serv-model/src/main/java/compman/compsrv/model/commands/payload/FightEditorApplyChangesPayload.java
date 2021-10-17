package compman.compsrv.model.commands.payload;

import compman.compsrv.annotations.CommandPayload;
import compman.compsrv.model.Payload;
import compman.compsrv.model.commands.CommandType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@NoArgsConstructor
@CommandPayload(type = CommandType.FIGHTS_EDITOR_APPLY_CHANGE)
@Accessors(chain = true)
public class FightEditorApplyChangesPayload implements Serializable, Payload {
    private String stageId;
    private FightsCompetitorUpdated[] bracketsChanges;
    private CompetitorMovedToGroup[] competitorMovedToGroups;
}
