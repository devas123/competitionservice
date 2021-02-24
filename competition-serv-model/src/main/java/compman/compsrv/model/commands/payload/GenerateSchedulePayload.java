package compman.compsrv.model.commands.payload;

import compman.compsrv.annotations.CommandPayload;
import compman.compsrv.model.Payload;
import compman.compsrv.model.commands.CommandType;
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO;
import compman.compsrv.model.dto.schedule.PeriodDTO;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@NoArgsConstructor
@Accessors(chain = true)
@CommandPayload(type = CommandType.GENERATE_SCHEDULE_COMMAND)
public class GenerateSchedulePayload implements Serializable, Payload {
    private PeriodDTO[] periods;
    private MatDescriptionDTO[] mats;
}
