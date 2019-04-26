package compman.compsrv.model.dto.schedule;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;

@Data
@AllArgsConstructor
@Accessors(chain = true)
@NoArgsConstructor
public class FightStartTimePairDTO {
    private String fightId;
    private String matId;
    private Integer fightNumber;
    private Instant startTime;
}
