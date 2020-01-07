package compman.compsrv.model.dto.dashboard;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@Getter
@Setter
public class MatDescriptionDTO {
    private String id;
    private String name;
    private String dashboardPeriodId;
}
