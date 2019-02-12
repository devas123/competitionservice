package compman.compsrv.model.dto.competition;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class CompScoreDTO {
    private CompetitorDTO competitor;
    private ScoreDTO score;
}