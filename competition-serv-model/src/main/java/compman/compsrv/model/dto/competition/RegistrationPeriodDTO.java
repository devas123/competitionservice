package compman.compsrv.model.dto.competition;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class RegistrationPeriodDTO {
    private String id;
    private String name;
    private String competitionId;
    private Instant start;
    private Instant end;
    private String[] registrationGroupIds;
}
