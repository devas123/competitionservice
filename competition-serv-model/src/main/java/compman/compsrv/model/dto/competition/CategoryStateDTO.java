package compman.compsrv.model.dto.competition;

import compman.compsrv.model.dto.brackets.BracketDescriptorDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class CategoryStateDTO {
    private String id;
    private String competitionId;
    private CategoryDescriptorDTO category;
    private CategoryStatus status;
    private BracketDescriptorDTO brackets;
    private Integer numberOfCompetitors;
    private CompetitorDTO[] competitors;
}
