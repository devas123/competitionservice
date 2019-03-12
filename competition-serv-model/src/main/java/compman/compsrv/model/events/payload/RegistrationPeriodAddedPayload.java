package compman.compsrv.model.events.payload;

import compman.compsrv.model.dto.competition.RegistrationPeriodDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegistrationPeriodAddedPayload implements Serializable {

    private RegistrationPeriodDTO period;
}
