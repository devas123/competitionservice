package compman.compsrv.model.events.payload;

import compman.compsrv.annotations.EventPayload;
import compman.compsrv.model.Payload;
import compman.compsrv.model.dto.competition.CompetitionStatus;
import compman.compsrv.model.events.EventType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EventPayload(type = {EventType.COMPETITION_STARTED, EventType.COMPETITION_PUBLISHED, EventType.COMPETITION_UNPUBLISHED, EventType.COMPETITION_STOPPED})
public class CompetitionStatusUpdatedPayload implements Serializable, Payload {
    private CompetitionStatus status;
}
