package compman.compsrv.model.dto.brackets;

import compman.compsrv.model.dto.competition.FightDescriptionDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class StageDescriptorDTO {
    private String id;
    private String name;
    private String categoryId;
    private String competitionId;
    private BracketType bracketType;
    private StageType stageType;
    private StageStatus stageStatus;
    private FightDescriptionDTO[] fights;
    private PointsAssignmentDescriptorDTO[] pointsAssignments;
    private StageResultDescriptorDTO stageResultDescriptor;
    private StageInputDescriptorDTO inputDescriptor;
    private Integer stageOrder;
    private Boolean waitForPrevious;
    private Boolean hasThirdPlaceFight;
}