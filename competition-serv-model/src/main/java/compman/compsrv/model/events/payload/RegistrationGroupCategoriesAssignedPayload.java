package compman.compsrv.model.events.payload;

import compman.compsrv.annotations.EventPayload;
import compman.compsrv.model.Payload;
import compman.compsrv.model.events.EventType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EventPayload(type = EventType.REGISTRATION_GROUP_CATEGORIES_ASSIGNED)
public class RegistrationGroupCategoriesAssignedPayload implements Serializable, Payload {
    private String periodId;
    private String groupId;
    private String[] categories;
}
