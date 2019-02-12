package compman.compsrv.model.commands.payload;

import compman.compsrv.model.dto.schedule.MatScheduleContainerDTO;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class UpdateCategoryFightsPayload implements Serializable {
    private MatScheduleContainerDTO[] fightsByMats;
}
