package compman.compsrv.model.dto.competition;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;


@Data
@NoArgsConstructor
@Accessors(chain = true)
public class PointGroupDTO {
    private String id;
    private String name;
    private Integer priority;
    private Integer value;
}
