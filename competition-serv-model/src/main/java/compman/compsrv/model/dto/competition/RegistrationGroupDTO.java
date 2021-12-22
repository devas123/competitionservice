package compman.compsrv.model.dto.competition;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;


@Data
@NoArgsConstructor
@Accessors(chain = true)
public class RegistrationGroupDTO {
    private String id;
    private String displayName;
    private Boolean defaultGroup;
    private BigDecimal registrationFee;
    private String[] categories;
}
