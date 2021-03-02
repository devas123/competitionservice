package compman.compsrv.model.events.payload;

import compman.compsrv.annotations.EventPayload;
import compman.compsrv.model.Payload;
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO;
import compman.compsrv.model.events.EventType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@Accessors(chain = true)
@EventPayload(type = EventType.MATS_UPDATED)
public class MatsUpdatedPayload implements Serializable, Payload {
    private MatDescriptionDTO[] mats;
}
