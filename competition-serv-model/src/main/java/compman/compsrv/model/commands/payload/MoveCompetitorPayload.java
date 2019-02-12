package compman.compsrv.model.commands.payload;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class MoveCompetitorPayload implements Serializable {

    private String competitorId;
    private String sourceFightId;
    private String targetFightId;
    private int index;
}
