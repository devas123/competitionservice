package compman.compsrv.model.commands.payload;

import compman.compsrv.model.dto.competition.CategoryDescriptorDTO;
import compman.compsrv.model.dto.competition.CompetitorDTO;
import lombok.Data;

import java.io.Serializable;

@Data
public class GenerateAbsoluteCategoryPayload implements Serializable {
    private CompetitorDTO[] competitors;
    private CategoryDescriptorDTO category;
    private String competitionId;
}
