package compman.compsrv.model.dto.competition;

import compman.compsrv.model.dto.brackets.BracketDescriptorDTO;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CategoryStateDTO {
    private String id;
    private String competitionId;
    private CategoryDescriptorDTO category;
    private CategoryStatus status;
    private BracketDescriptorDTO brackets;
    private Integer numberOfCompetitors;
}
