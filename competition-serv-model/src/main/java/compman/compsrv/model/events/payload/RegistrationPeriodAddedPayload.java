package compman.compsrv.model.events.payload;

import compman.compsrv.model.commands.payload.Payload;
import compman.compsrv.model.dto.competition.RegistrationPeriodDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegistrationPeriodAddedPayload implements Serializable, Payload {

    private RegistrationPeriodDTO period;
}
