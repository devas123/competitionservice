package compman.compsrv.model.commands.payload;

import compman.compsrv.model.dto.competition.CompetitorDTO;
import lombok.Data;

import java.io.Serializable;

@Data
public class UpdateCompetitorPayload implements Serializable {
    private CompetitorDTO competitor;
}
