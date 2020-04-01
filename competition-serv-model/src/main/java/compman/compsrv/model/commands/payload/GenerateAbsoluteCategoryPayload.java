package compman.compsrv.model.commands.payload;

import compman.compsrv.model.dto.competition.CategoryDescriptorDTO;
import compman.compsrv.model.dto.competition.CompetitorDTO;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class GenerateAbsoluteCategoryPayload implements Serializable, Payload {
    private CompetitorDTO[] competitors;
    private CategoryDescriptorDTO category;
    private String competitionId;
}
