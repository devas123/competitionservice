package compman.compsrv.model.events.payload;

import compman.compsrv.model.dto.competition.CompetitorDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class  CompetitorUpdatedPayload implements Serializable {

    private CompetitorDTO fighter;
}
