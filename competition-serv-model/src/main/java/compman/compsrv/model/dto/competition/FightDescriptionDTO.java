package compman.compsrv.model.dto.competition;

import compman.compsrv.model.dto.brackets.StageRoundType;
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class FightDescriptionDTO {
    private String id;
    private String categoryId;
    private String fightName;
    private String winFight;
    private String loseFight;
    private CompScoreDTO[] scores;
    private Integer duration;
    private Integer round;
    private Boolean invalid;
    private StageRoundType roundType;
    private FightStatus status;
    private FightResultDTO fightResult;
    private MatDescriptionDTO mat;
    private Integer numberOnMat;
    private Integer priority;
    private String competitionId;
    private String period;
    private Instant startTime;
    private String stageId;
    private String groupId;
    private String scheduleEntryId;
    private Integer numberInRound;
}
