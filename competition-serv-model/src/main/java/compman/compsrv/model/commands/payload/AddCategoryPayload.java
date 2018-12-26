package compman.compsrv.model.commands.payload;

import compman.compsrv.model.dto.competition.CategoryDescriptorDTO;
import lombok.Data;

import java.io.Serializable;

@Data
public class AddCategoryPayload implements Serializable {
    private CategoryDescriptorDTO category;
}
