package compman.compsrv.model.dto.brackets;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class AdditionalGroupSortingDescriptorDTO {
    private GroupSortDirection groupSortDirection;
    private GroupSortSpecifier groupSortSpecifier;
}
