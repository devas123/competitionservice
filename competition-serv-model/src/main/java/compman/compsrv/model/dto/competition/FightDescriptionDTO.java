package compman.compsrv.model.dto.competition;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
@AllArgsConstructor
public class FightDescriptionDTO {
    private String id;
    private String categoryId;
    private String winFight;
    private String loseFight;
    private CompScoreDTO[] scores;
    private String parentId1;
    private String parentId2;
    private Long duration;
    private Integer round;
    private FightStage stage;
    private FightResultDTO fightResult;
    private String matId;
    private Integer numberOnMat;
    private Integer priority;
    private String competitionId;
    private String period;
    private ZonedDateTime startTime;
    private Integer numberInRound;
}
