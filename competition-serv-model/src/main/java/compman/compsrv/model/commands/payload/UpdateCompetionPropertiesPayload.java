package compman.compsrv.model.commands.payload;

import compman.compsrv.annotations.CommandPayload;
import compman.compsrv.model.Payload;
import compman.compsrv.model.commands.CommandType;
import compman.compsrv.model.dto.competition.CompetitionPropertiesDTO;
import compman.compsrv.model.dto.competition.CompetitorDTO;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@CommandPayload(type = CommandType.UPDATE_COMPETITION_PROPERTIES_COMMAND)
public class UpdateCompetionPropertiesPayload implements Serializable, Payload {
    private CompetitionPropertiesDTO competitionProperties;
}
