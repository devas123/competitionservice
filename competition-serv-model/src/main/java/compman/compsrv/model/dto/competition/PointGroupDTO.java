package compman.compsrv.model.dto.competition;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class PointGroupDTO {
    private String id;
    private String name;
    private BigDecimal priority;
    private BigDecimal value;
}
