package compman.compsrv.model.dto.brackets;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class CompetitorStageResultDTO {
    private String competitorId;
    private Integer points;
    private Integer round;
    private StageRoundType roundType;
    private Integer place;
    private String stageId;
    private String groupId;
    private Boolean conflicting;
}
