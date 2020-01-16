package compman.compsrv.model.events.payload;

import compman.compsrv.model.dto.competition.RegistrationGroupDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegistrationGroupAddedPayload implements Serializable {

    private String periodId;
    private RegistrationGroupDTO[] groups;
}
