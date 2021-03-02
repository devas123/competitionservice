package compman.compsrv.model.events.payload;

import compman.compsrv.model.dto.dashboard.MatDescriptionDTO;
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
    private MatDescriptionDTO mat;
}
