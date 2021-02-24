package compman.compsrv.model.commands.payload;

import compman.compsrv.annotations.CommandPayload;
import compman.compsrv.annotations.EventPayload;
import compman.compsrv.model.Payload;
import compman.compsrv.model.commands.CommandType;
import compman.compsrv.model.events.EventType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@EventPayload(type = EventType.CATEGORY_REGISTRATION_STATUS_CHANGED)
@CommandPayload(type = CommandType.CHANGE_CATEGORY_REGISTRATION_STATUS_COMMAND)
public class CategoryRegistrationStatusChangePayload implements Serializable, Payload {
    private boolean newStatus;
}
