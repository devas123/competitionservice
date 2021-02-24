package compman.compsrv.model.events.payload;

import compman.compsrv.annotations.EventPayload;
import compman.compsrv.model.Payload;
import compman.compsrv.model.dto.competition.CompetitionPropertiesDTO;
import compman.compsrv.model.dto.competition.RegistrationInfoDTO;
import compman.compsrv.model.events.EventType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EventPayload(type = EventType.COMPETITION_CREATED)
public class CompetitionCreatedPayload implements Serializable, Payload {
    private CompetitionPropertiesDTO properties;
    private RegistrationInfoDTO reginfo;
}
