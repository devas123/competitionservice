package compman.compsrv.model.commands.payload;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class FightEditorApplyChangesPayload implements Serializable, Payload {
    private String stageId;
    private FightEditorChange[] bracketsChanges;
    private CompetitorGroupChange[] competitorGroupChanges;
}
