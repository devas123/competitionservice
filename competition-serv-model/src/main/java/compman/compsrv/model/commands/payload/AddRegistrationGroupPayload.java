package compman.compsrv.model.commands.payload;

import compman.compsrv.annotations.CommandPayload;
import compman.compsrv.model.Payload;
import compman.compsrv.model.commands.CommandType;
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
@CommandPayload(type = CommandType.ADD_REGISTRATION_GROUP_COMMAND)
public class AddRegistrationGroupPayload implements Serializable, Payload {
    private String periodId;
    private RegistrationGroupDTO[] groups;
}
