package compman.compsrv.model.commands.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class RegistrationPeriodAddRegistrationGroupPayload {
    private String groupId;
    private String periodId;
}