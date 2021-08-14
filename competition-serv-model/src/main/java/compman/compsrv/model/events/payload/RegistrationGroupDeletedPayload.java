package compman.compsrv.model.events.payload;

import compman.compsrv.annotations.EventPayload;
import compman.compsrv.model.Payload;
import compman.compsrv.model.events.EventType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EventPayload(type = EventType.REGISTRATION_GROUP_DELETED)
@Accessors(chain = true)
public class RegistrationGroupDeletedPayload implements Serializable, Payload {
    private String periodId;
    private String groupId;
}
