package compman.compsrv.model.dto.competition;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Map;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class RegistrationInfoDTO {
    private String id;
    private Boolean registrationOpen;
    private Map<String, RegistrationPeriodDTO> registrationPeriods;
    private Map<String, RegistrationGroupDTO> registrationGroups;
}
