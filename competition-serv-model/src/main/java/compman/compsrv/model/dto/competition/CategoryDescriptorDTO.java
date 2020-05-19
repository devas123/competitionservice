package compman.compsrv.model.dto.competition;

import lombok.*;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class CategoryDescriptorDTO {
    private CategoryRestrictionDTO[] restrictions;
    private String id;
    private String name;
    private Boolean registrationOpen;
    private BigDecimal fightDuration;
}
