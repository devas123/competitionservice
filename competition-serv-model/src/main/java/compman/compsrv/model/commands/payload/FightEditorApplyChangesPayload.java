package compman.compsrv.model.commands.payload;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Timer;

@Data
@NoArgsConstructor
public class FightEditorApplyChangesPayload implements Serializable, Payload {
    private FightChanges[] changes;
}
