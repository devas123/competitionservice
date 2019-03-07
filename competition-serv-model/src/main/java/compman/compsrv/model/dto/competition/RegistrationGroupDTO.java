package compman.compsrv.model.dto.competition;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class RegistrationGroupDTO {
    private Long id;
    private String displayName;
    private BigDecimal registrationFee;
}
