package compman.compsrv.model.dto.brackets;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class StageInputDescriptorDTO {
    private String id;
    private Integer numberOfCompetitors;
    private CompetitorSelectorDTO[] selectors;
    private DistributionType distributionType;
}
