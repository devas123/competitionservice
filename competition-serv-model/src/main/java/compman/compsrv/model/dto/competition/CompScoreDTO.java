package compman.compsrv.model.dto.competition;

import compman.compsrv.model.dto.brackets.FightReferenceType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class CompScoreDTO {
    private String placeholderId;
    private String competitorId;
    private ScoreDTO score;
    private Integer order;
    private FightReferenceType parentReferenceType;
    private String parentFightId;
}
