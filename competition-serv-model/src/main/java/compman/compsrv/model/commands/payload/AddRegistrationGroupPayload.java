package compman.compsrv.model.commands.payload;

import compman.compsrv.model.dto.competition.RegistrationGroupDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class AddRegistrationGroupPayload implements Serializable {
    private String periodId;
    private RegistrationGroupDTO group;
}