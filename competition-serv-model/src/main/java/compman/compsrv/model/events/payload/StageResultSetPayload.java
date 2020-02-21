package compman.compsrv.model.events.payload;

import compman.compsrv.model.dto.brackets.CompetitorStageResultDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class StageResultSetPayload implements Serializable {
    private String stageId;
    private CompetitorStageResultDTO[] results;
}
