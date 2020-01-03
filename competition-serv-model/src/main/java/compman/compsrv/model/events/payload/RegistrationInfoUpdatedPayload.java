package compman.compsrv.model.events.payload;

import compman.compsrv.model.dto.competition.RegistrationInfoDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class RegistrationInfoUpdatedPayload {
    private RegistrationInfoDTO registrationInfo;
}
