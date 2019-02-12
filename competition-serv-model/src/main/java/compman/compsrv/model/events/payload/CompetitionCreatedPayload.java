package compman.compsrv.model.events.payload;

import compman.compsrv.model.dto.competition.CompetitionPropertiesDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CompetitionCreatedPayload implements Serializable {
    private CompetitionPropertiesDTO properties;
}
