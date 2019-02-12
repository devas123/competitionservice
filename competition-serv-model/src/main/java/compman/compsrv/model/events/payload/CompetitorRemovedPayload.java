package compman.compsrv.model.events.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CompetitorRemovedPayload implements Serializable {
    private String fighterId;
}
