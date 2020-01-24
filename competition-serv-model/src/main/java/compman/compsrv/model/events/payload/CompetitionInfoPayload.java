package compman.compsrv.model.events.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true
)
public class CompetitionInfoPayload implements Serializable {
    private String competitionId;
    private String memberId;
    private String host;
    private Integer port;
}