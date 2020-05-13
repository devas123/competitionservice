package compman.compsrv.model.events.payload;

import compman.compsrv.model.commands.payload.Payload;
import compman.compsrv.model.dto.competition.CompetitionPropertiesDTO;
import compman.compsrv.model.dto.competition.RegistrationInfoDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CompetitionCreatedPayload implements Serializable, Payload {
    private CompetitionPropertiesDTO properties;
    private RegistrationInfoDTO reginfo;
}
