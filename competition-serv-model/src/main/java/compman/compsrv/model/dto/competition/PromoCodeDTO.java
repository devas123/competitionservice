package compman.compsrv.model.dto.competition;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class PromoCodeDTO {
    private String id;
    private BigDecimal coefficient;
    private String competitionId;
    private ZonedDateTime startAt;
    private ZonedDateTime expireAt;
}
