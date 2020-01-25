package compman.compsrv.model.dto.brackets;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class PointsAssignmentDescriptorDTO {
    private String id;
    private CompetitorResultType classifier;
    private BigDecimal points;
    private BigDecimal additionalPoints;
}
