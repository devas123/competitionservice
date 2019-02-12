package compman.compsrv.model.events.payload;

import compman.compsrv.model.dto.competition.CompetitionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CompetitionStatusUpdatedPayload implements Serializable {

    private CompetitionStatus status;
}
