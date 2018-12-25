package compman.compsrv.model.dto.competition;

import lombok.Data;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Data
public class PromoCodeDTO {
    private String id;
    private BigDecimal coefficient;
    private String competitionId;
    private ZonedDateTime startAt;
    private ZonedDateTime expireAt;
}
