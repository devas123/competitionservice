package compman.compsrv.model.commands.payload;

import lombok.Data;

import java.io.Serializable;

@Data
public class MoveCompetitorPayload implements Serializable {

    private String competitorId;
    private String sourceFightId;
    private String targetFightId;
    private int index;
}
