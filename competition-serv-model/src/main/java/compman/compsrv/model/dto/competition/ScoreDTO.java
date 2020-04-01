package compman.compsrv.model.dto.competition;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class ScoreDTO {
    private Integer points;
    private Integer advantages;
    private Integer penalties;
    private PointGroupDTO[] pointGroups;
}
