package compman.compsrv.model.events.payload;

import compman.compsrv.model.dto.competition.CompetitionPropertiesDTO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class CompetitionCreatedPayload implements Serializable {
    private CompetitionPropertiesDTO properties;
}
