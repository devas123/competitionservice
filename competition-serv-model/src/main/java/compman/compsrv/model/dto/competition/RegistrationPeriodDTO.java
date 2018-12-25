package compman.compsrv.model.dto.competition;

import lombok.Data;

import java.time.ZonedDateTime;

@Data
public class RegistrationPeriodDTO {
    private Long id;
    private ZonedDateTime start;
    private ZonedDateTime end;
    private RegistrationGroupDTO[] registrationGroups;
}
