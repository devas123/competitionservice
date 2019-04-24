package compman.compsrv.model.commands.payload;

import compman.compsrv.model.dto.schedule.MatScheduleContainerDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class UpdateFightsStartTimePayload implements Serializable {
    private MatScheduleContainerDTO[] fightsByMats;
}
