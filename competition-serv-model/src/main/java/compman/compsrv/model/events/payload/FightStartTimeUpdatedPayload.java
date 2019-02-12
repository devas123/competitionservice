package compman.compsrv.model.events.payload;

import compman.compsrv.model.dto.competition.FightDescriptionDTO;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class FightStartTimeUpdatedPayload implements Serializable {
    private FightDescriptionDTO[] newFights;
}
