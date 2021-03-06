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
@Accessors(chain = true
)
@EventPayload(type = EventType.INTERNAL_COMPETITION_INFO)
public class CompetitionInfoPayload implements Serializable, Payload {
    private String competitionId;
    private String memberId;
    private String host;
    private Integer port;
}
