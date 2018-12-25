package compman.compsrv.model.events.payload;

import compman.compsrv.model.dto.schedule.ScheduleDTO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class ScheduleGeneratedPayload implements Serializable {
    private ScheduleDTO schedule;
}
