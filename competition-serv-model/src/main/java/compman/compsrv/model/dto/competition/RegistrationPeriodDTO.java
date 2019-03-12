package compman.compsrv.model.dto.competition;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.ZonedDateTime;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class RegistrationPeriodDTO {
    private Long id;
    private String name;
    private ZonedDateTime start;
    private ZonedDateTime end;
    private RegistrationGroupDTO[] registrationGroups;
}
