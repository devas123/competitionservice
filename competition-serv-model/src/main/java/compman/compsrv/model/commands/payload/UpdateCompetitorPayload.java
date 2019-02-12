package compman.compsrv.model.commands.payload;

import compman.compsrv.model.dto.competition.CompetitorDTO;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class UpdateCompetitorPayload implements Serializable {
    private CompetitorDTO competitor;
}
