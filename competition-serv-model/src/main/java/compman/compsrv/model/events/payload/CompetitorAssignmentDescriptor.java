package compman.compsrv.model.events.payload;

import compman.compsrv.model.dto.brackets.FightReferenceType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class CompetitorAssignmentDescriptor {
    private String fromFightId;
    private String toFightId;
    private String competitorId;
    private FightReferenceType referenceType;
}
