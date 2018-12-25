package compman.compsrv.model.dto.brackets;

import compman.compsrv.model.dto.competition.FightDescriptionDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@Accessors(chain = true)
public class BracketDescriptorDTO {
    private String id;
    private String competitionId;
    private BracketType bracketType;
    private FightDescriptionDTO[] fights;
}
