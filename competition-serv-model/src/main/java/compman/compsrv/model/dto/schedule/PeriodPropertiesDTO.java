package compman.compsrv.model.dto.schedule;

import compman.compsrv.model.dto.competition.CategoryDescriptorDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@AllArgsConstructor
@Accessors(chain = true)
@NoArgsConstructor
public class PeriodPropertiesDTO {
    private String id;
    private String name;
    private Instant startTime;
    private Integer numberOfMats;
    private Integer timeBetweenFights;
    private BigDecimal riskPercent;
    private CategoryDescriptorDTO[] categories;
}
