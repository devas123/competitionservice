package compman.compsrv.model.commands.payload;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class RemoveCompetitorPayload implements Serializable, Payload {
    private String competitorId;
}
