package compman.compsrv.model.dto.competition;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class PromoCodeDTO {
    private String id;
    private Integer coefficient;
    private String competitionId;
    private Instant startAt;
    private Instant expireAt;
}
