package compman.compsrv.model.dto.competition;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class FightResultDTO {
    private String winnerId;
    private String resultTypeId;
    private String reason;
}
