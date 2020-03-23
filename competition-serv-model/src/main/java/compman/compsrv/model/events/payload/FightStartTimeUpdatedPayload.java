package compman.compsrv.model.events.payload;

import compman.compsrv.model.commands.payload.Payload;
import compman.compsrv.model.dto.schedule.FightStartTimePairDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class FightStartTimeUpdatedPayload implements Serializable, Payload {
    private FightStartTimePairDTO[] newFights;
}
