package compman.compsrv.model.commands.payload;

import compman.compsrv.model.dto.competition.CompetitionPropertiesDTO;
import compman.compsrv.model.dto.competition.RegistrationInfoDTO;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class CreateCompetitionPayload implements Serializable, Payload {
    private CompetitionPropertiesDTO properties;
    private RegistrationInfoDTO reginfo;
}
