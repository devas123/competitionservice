package compman.compsrv.model.events.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;

@Data
@AllArgsConstructor
@Accessors(chain = true)
@NoArgsConstructor
public class FightPropertiesUpdate {
    private String fightId;
    private Integer numberOnMat;
    private Instant startTime;
    private String matId;
}
