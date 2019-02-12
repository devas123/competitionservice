package compman.compsrv.model.events.payload;

import compman.compsrv.model.dto.schedule.ScheduleDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScheduleGeneratedPayload implements Serializable {
    private ScheduleDTO schedule;
}
