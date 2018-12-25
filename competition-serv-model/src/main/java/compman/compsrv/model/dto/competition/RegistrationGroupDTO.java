package compman.compsrv.model.dto.competition;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RegistrationGroupDTO {
    private Long id;
    private String displayName;
    private BigDecimal registrationFee;
}
