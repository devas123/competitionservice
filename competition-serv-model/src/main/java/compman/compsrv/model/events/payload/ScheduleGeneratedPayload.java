package compman.compsrv.model.events.payload;

import compman.compsrv.annotations.EventPayload;
import compman.compsrv.model.Payload;
import compman.compsrv.model.dto.schedule.ScheduleDTO;
import compman.compsrv.model.events.EventType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EventPayload(type = EventType.SCHEDULE_GENERATED)
public class ScheduleGeneratedPayload implements Serializable, Payload {
    private ScheduleDTO schedule;
}
