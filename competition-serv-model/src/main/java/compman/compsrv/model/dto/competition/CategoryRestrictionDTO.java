package compman.compsrv.model.dto.competition;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class CategoryRestrictionDTO {
    private String id;
    private CategoryRestrictionType type;
    private String name;
    private String minValue;
    private String maxValue;
    private String unit;
}
