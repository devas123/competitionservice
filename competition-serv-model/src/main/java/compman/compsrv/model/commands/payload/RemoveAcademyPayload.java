package compman.compsrv.model.commands.payload;

import compman.compsrv.annotations.CommandPayload;
import compman.compsrv.annotations.EventPayload;
import compman.compsrv.model.Payload;
import compman.compsrv.model.commands.CommandType;
import compman.compsrv.model.dto.competition.AcademyDTO;
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
@CommandPayload(type = CommandType.REMOVE_ACADEMY_COMMAND)
@EventPayload(type = EventType.ACADEMY_REMOVED)
public class RemoveAcademyPayload implements Serializable, Payload {
    private AcademyDTO academy;
}
