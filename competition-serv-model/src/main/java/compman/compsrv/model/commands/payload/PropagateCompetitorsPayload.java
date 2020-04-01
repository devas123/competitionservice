package compman.compsrv.model.commands.payload;


import compman.compsrv.model.dto.brackets.CompetitorSelectorDTO;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class PropagateCompetitorsPayload implements Serializable, Payload {
    private boolean manualOverride;
    private String propagateToStageId;
    private String previousStageId;
    private CompetitorSelectorDTO[] selectorOverrides;
}
