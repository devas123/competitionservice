package compman.compsrv.model.dto.brackets;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class StageResultDescriptorDTO {
    private String id;
    private String name;
    private boolean forceManualAssignment;
    private Integer outputSize;
    private FightResultOptionDTO[] fightResultOptions;
    private CompetitorStageResultDTO[] competitorResults;
    private AdditionalGroupSortingDescriptorDTO[] additionalGroupSortingDescriptors;
}
