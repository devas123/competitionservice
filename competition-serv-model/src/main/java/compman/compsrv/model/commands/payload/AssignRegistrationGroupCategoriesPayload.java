package compman.compsrv.model.commands.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AssignRegistrationGroupCategoriesPayload implements Serializable {
    private String periodId;
    private String groupId;
    private String[] categories;
}
