package compman.compsrv.model.commands.payload;

import compman.compsrv.model.dto.competition.CategoryDescriptorDTO;
import compman.compsrv.model.dto.competition.CompetitorDTO;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class ChangeCompetitorCategoryPayload implements Serializable {
    private CategoryDescriptorDTO newCategory;
    private CompetitorDTO fighter;
}
