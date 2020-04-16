package compman.compsrv.model.events.payload;

import compman.compsrv.model.commands.payload.Payload;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class CompetitorsPropagatedToStagePayload implements Serializable, Payload {
    private String stageId;
    private List<CompetitorAssignmentDescriptor> propagations;
}
