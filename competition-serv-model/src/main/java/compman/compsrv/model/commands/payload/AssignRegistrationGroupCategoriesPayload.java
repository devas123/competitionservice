package compman.compsrv.model.commands.payload;

import compman.compsrv.annotations.CommandPayload;
import compman.compsrv.model.Payload;
import compman.compsrv.model.commands.CommandType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@CommandPayload(type = CommandType.ASSIGN_REGISTRATION_GROUP_CATEGORIES_COMMAND)
public class AssignRegistrationGroupCategoriesPayload implements Serializable, Payload {
    private String periodId;
    private String groupId;
    private String[] categories;
}
