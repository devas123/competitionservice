package compman.compsrv.model.events.payload;

import compman.compsrv.model.dto.competition.FightDescriptionDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FightsAddedToStagePayload implements Serializable {
    private FightDescriptionDTO[] fights;
    private String stageId;
}