package compman.compsrv.model.commands.payload;

import compman.compsrv.annotations.CommandPayload;
import compman.compsrv.model.Payload;
import compman.compsrv.model.commands.CommandType;
import compman.compsrv.model.dto.competition.CompetitionPropertiesDTO;
import compman.compsrv.model.dto.competition.RegistrationInfoDTO;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@NoArgsConstructor
@CommandPayload(type = CommandType.CREATE_COMPETITION_COMMAND)
@Accessors(chain = true)
public class CreateCompetitionPayload implements Serializable, Payload {
    private CompetitionPropertiesDTO properties;
    private RegistrationInfoDTO reginfo;
}
