package compman.compsrv.model.commands.payload;


import compman.compsrv.annotations.CommandPayload;
import compman.compsrv.model.Payload;
import compman.compsrv.model.commands.CommandType;
import compman.compsrv.model.dto.brackets.CompetitorSelectorDTO;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@NoArgsConstructor
@Accessors(chain = true)
@CommandPayload(type = CommandType.PROPAGATE_COMPETITORS_COMMAND)
public class PropagateCompetitorsPayload implements Serializable, Payload {
    private boolean manualOverride;
    private String propagateToStageId;
    private String previousStageId;
    private CompetitorSelectorDTO[] selectorOverrides;
}
