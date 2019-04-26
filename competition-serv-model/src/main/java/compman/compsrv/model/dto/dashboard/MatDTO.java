package compman.compsrv.model.dto.dashboard;

import compman.compsrv.model.dto.competition.FightDescriptionDTO;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@Getter
@Setter
public class MatDTO {
    private String matId;
    private Integer numberOfFights;
    private FightDescriptionDTO[] topFiveFights;
}
