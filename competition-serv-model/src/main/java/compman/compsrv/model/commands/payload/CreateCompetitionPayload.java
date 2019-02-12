package compman.compsrv.model.commands.payload;

import compman.compsrv.model.dto.competition.CompetitionPropertiesDTO;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class CreateCompetitionPayload implements Serializable {
    private CompetitionPropertiesDTO properties;
}
