package compman.compsrv.model.events.payload;

import compman.compsrv.model.dto.competition.FightDescriptionDTO;
import lombok.Data;

import java.io.Serializable;

@Data
public class FightStartTimeUpdatedPayload implements Serializable {
    private FightDescriptionDTO[] newFights;
}
