package compman.compsrv.model.events.payload;

import compman.compsrv.model.dto.competition.CompetitorDTO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class CompetitorAddedPayload implements Serializable {

    private CompetitorDTO fighter;
}
