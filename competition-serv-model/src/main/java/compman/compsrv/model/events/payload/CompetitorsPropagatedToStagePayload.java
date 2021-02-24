package compman.compsrv.model.events.payload;

import compman.compsrv.annotations.EventPayload;
import compman.compsrv.model.Payload;
import compman.compsrv.model.events.EventType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@Accessors(chain = true)
@EventPayload(type = EventType.COMPETITORS_PROPAGATED_TO_STAGE)
public class CompetitorsPropagatedToStagePayload implements Serializable, Payload {
    private String stageId;
    private List<CompetitorAssignmentDescriptor> propagations;
}
