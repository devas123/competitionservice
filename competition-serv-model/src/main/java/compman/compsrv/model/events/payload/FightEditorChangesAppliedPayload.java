package compman.compsrv.model.events.payload;

import compman.compsrv.annotations.EventPayload;
import compman.compsrv.model.Payload;
import compman.compsrv.model.dto.competition.FightDescriptionDTO;
import compman.compsrv.model.events.EventType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@EventPayload(type = EventType.FIGHTS_EDITOR_CHANGE_APPLIED)
public class FightEditorChangesAppliedPayload implements Serializable, Payload {
    private FightDescriptionDTO[] updates;
    private FightDescriptionDTO[] newFights;
    private String[] removedFighids;
}
