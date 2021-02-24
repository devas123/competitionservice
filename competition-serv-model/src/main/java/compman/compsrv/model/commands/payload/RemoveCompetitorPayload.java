package compman.compsrv.model.commands.payload;

import compman.compsrv.annotations.CommandPayload;
import compman.compsrv.model.Payload;
import compman.compsrv.model.commands.CommandType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@CommandPayload(type = CommandType.REMOVE_COMPETITOR_COMMAND)
public class RemoveCompetitorPayload implements Serializable, Payload {
    private String competitorId;
}
