package compman.compsrv.model.commands.payload;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class ChangeCompetitorCategoryPayload implements Serializable, Payload {
    private String newCategoryId;
    private String oldCategoryId;
    private String fighterId;
}
