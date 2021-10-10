package compman.compsrv.model.dto.brackets;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Arrays;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class FightResultOptionDTO {
    public static final FightResultOptionDTO WIN_POINTS = new FightResultOptionDTO()
            .setId("_default_win_points")
            .setWinnerPoints(3)
            .setWinnerAdditionalPoints(1)
            .setDescription("Win by points")
            .setShortName("Win points");
    public static final FightResultOptionDTO WIN_SUBMISSION = new FightResultOptionDTO()
            .setId("_default_win_submission")
            .setWinnerPoints(3)
            .setWinnerAdditionalPoints(2)
            .setDescription("Win by submission")
            .setShortName("Win submission");
    public static final FightResultOptionDTO WIN_DECISION = new FightResultOptionDTO().setId("_default_win_decision").setWinnerPoints(3).setWinnerAdditionalPoints(0).setDescription("Win by decision")
            .setShortName("Win decision");
    public static final FightResultOptionDTO OPPONENT_DQ = new FightResultOptionDTO().setId("_default_opponent_dq").setWinnerPoints(3).setWinnerAdditionalPoints(0).setDescription("Win because opponent was disqualified")
            .setShortName("Win opponent DQ");
    public static final FightResultOptionDTO WALKOVER = new FightResultOptionDTO().setId("_default_walkover").setWinnerPoints(3).setWinnerAdditionalPoints(0).setDescription("Win because opponent didn't show up or got injured, or some other reason.")
            .setShortName("Win walkover");
    public static final FightResultOptionDTO BOTH_DQ = new FightResultOptionDTO()
            .setId("_default_both_dq")
            .setWinnerPoints(0)
            .setWinnerAdditionalPoints(0)
            .setLoserPoints(0)
            .setLoserAdditionalPoints(0)
            .setDescription("Both competitors were disqualified")
            .setDraw(true)
            .setShortName("Both DQ");
    public static final FightResultOptionDTO DRAW = new FightResultOptionDTO().setId("_default_draw")
            .setWinnerPoints(1)
            .setWinnerAdditionalPoints(0)
            .setLoserPoints(1)
            .setLoserAdditionalPoints(0)
            .setDraw(true)
            .setDescription("Draw")
            .setShortName("Draw");

    public static final List<FightResultOptionDTO> values = Arrays.asList(
            WIN_POINTS,
            WIN_SUBMISSION,
            WIN_DECISION,
            OPPONENT_DQ,
            WALKOVER,
            BOTH_DQ,
            DRAW);

    private String id;
    private String description;
    private String shortName;
    private boolean draw;
    private Integer winnerPoints;
    private Integer winnerAdditionalPoints = 0;
    private Integer loserPoints = 0;
    private Integer loserAdditionalPoints = 0;
}
