package compman.compsrv.model.dto.brackets;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class FightResultOptionDTO {
    public static final FightResultOptionDTO WIN_POINTS = new FightResultOptionDTO()
            .setId("_default_win_points")
            .setWinnerPoints(BigDecimal.valueOf(3))
            .setWinnerAdditionalPoints(BigDecimal.valueOf(1))
            .setDescription("Win by points")
            .setShortName("Win points");
    public static final FightResultOptionDTO WIN_SUBMISSION = new FightResultOptionDTO()
            .setId("_default_win_submission")
            .setWinnerPoints(BigDecimal.valueOf(3))
            .setWinnerAdditionalPoints(BigDecimal.valueOf(2))
            .setDescription("Win by submission")
            .setShortName("Win submission");
    public static final FightResultOptionDTO WIN_DECISION = new FightResultOptionDTO().setId("_default_win_decision").setWinnerPoints(BigDecimal.valueOf(3)).setWinnerAdditionalPoints(BigDecimal.valueOf(0)).setDescription("Win by decision")
            .setShortName("Win decision");
    public static final FightResultOptionDTO OPPONENT_DQ = new FightResultOptionDTO().setId("_default_opponent_dq").setWinnerPoints(BigDecimal.valueOf(3)).setWinnerAdditionalPoints(BigDecimal.valueOf(0)).setDescription("Win because opponent was disqualified")
            .setShortName("Win opponent DQ");
    public static final FightResultOptionDTO WALKOVER = new FightResultOptionDTO().setId("_default_walkover").setWinnerPoints(BigDecimal.valueOf(3)).setWinnerAdditionalPoints(BigDecimal.valueOf(0)).setDescription("Win because opponent didn't show up or got injured, or some other reason.")
            .setShortName("Win walkover");
    public static final FightResultOptionDTO BOTH_DQ = new FightResultOptionDTO()
            .setId("_default_both_dq")
            .setWinnerPoints(BigDecimal.valueOf(0))
            .setWinnerAdditionalPoints(BigDecimal.valueOf(0))
            .setLoserPoints(BigDecimal.valueOf(0))
            .setLoserAdditionalPoints(BigDecimal.valueOf(0))
            .setDescription("Both competitors were disqualified")
            .setDraw(true)
            .setShortName("Both DQ");
    public static final FightResultOptionDTO DRAW = new FightResultOptionDTO().setId("_default_draw")
            .setWinnerPoints(BigDecimal.valueOf(1))
            .setWinnerAdditionalPoints(BigDecimal.valueOf(0))
            .setLoserPoints(BigDecimal.valueOf(1))
            .setLoserAdditionalPoints(BigDecimal.valueOf(0))
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
    private BigDecimal winnerPoints;
    private BigDecimal winnerAdditionalPoints = BigDecimal.ZERO;
    private BigDecimal loserPoints = BigDecimal.ZERO;
    private BigDecimal loserAdditionalPoints = BigDecimal.ZERO;
}
