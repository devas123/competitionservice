package compman.compsrv.model.commands.payload;

import compman.compsrv.model.dto.schedule.MatScheduleContainerDTO;
import lombok.Data;

import java.io.Serializable;

@Data
public class UpdateCategoryFightsPayload implements Serializable {
    private MatScheduleContainerDTO[] fightsByMats;
}
