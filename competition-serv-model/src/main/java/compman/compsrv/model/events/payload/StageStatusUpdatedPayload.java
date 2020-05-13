package compman.compsrv.model.events.payload;

import compman.compsrv.model.commands.payload.Payload;
import compman.compsrv.model.dto.brackets.CompetitorStageResultDTO;
import compman.compsrv.model.dto.brackets.StageStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class StageStatusUpdatedPayload implements Serializable, Payload {
    private String stageId;
    private StageStatus status;
}
