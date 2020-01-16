package compman.compsrv.model.dto.competition;

import compman.compsrv.model.dto.brackets.CompetitorResultType;
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
    private CompetitorResultType resultType;
    private String reason;
}
