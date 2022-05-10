package compman.compsrv.model.commands.payload;

import compman.compsrv.annotations.CommandPayload;
import compman.compsrv.annotations.EventPayload;
import compman.compsrv.model.Payload;
import compman.compsrv.model.commands.CommandType;
import compman.compsrv.model.events.EventType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@CommandPayload(type = CommandType.CHANGE_COMPETITOR_CATEGORY_COMMAND)
@EventPayload(type = EventType.COMPETITOR_CATEGORY_CHANGED)
public class ChangeCompetitorCategoryPayload implements Serializable, Payload {
    private String[] newCategories;
    private String[] oldCategories;
    private String fighterId;
}
