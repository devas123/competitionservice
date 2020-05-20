package compman.compsrv.model.dto.competition;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class CategoryDescriptorDTO {
    private CategoryRestrictionDTO[] restrictions;
    private String id;
    private String name;
    private Boolean registrationOpen;
}
