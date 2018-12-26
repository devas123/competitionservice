package compman.compsrv.model.dto.competition;

import lombok.Data;

@Data
public class RegistrationInfoDTO {
    private String id;
    private RegistrationPeriodDTO[] registrationPeriods;
}
