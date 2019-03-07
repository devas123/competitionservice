package compman.compsrv.model.dto.competition;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class RegistrationInfoDTO {
    private String id;
    private RegistrationPeriodDTO[] registrationPeriods;
}
