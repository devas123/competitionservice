package compman.compsrv.model.events.payload;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class CompetitorRemovedPayload implements Serializable {
    private String fighterId;
}
