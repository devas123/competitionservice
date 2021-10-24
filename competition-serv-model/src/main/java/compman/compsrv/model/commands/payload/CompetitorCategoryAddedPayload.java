package compman.compsrv.model.commands.payload;

import compman.compsrv.annotations.EventPayload;
import compman.compsrv.model.Payload;
import compman.compsrv.model.events.EventType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@NoArgsConstructor
@EventPayload(type = EventType.COMPETITOR_CATEGORY_ADDED)
@Accessors(chain = true)
public class CompetitorCategoryAddedPayload implements Serializable, Payload {
    private String newCategoryId;
    private String fighterId;
}
