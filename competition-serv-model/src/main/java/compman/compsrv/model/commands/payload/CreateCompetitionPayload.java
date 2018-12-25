package compman.compsrv.model.commands.payload;

import compman.compsrv.model.dto.competition.CompetitionPropertiesDTO;
import lombok.Data;

import java.io.Serializable;

@Data
public class CreateCompetitionPayload implements Serializable {
    private CompetitionPropertiesDTO properties;
}
