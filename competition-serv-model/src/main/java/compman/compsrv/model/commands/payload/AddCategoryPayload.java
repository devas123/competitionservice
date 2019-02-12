package compman.compsrv.model.commands.payload;

import compman.compsrv.model.dto.competition.CategoryDescriptorDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AddCategoryPayload implements Serializable {
    private CategoryDescriptorDTO category;
}
