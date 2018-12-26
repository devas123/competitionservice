package compman.compsrv.model.dto.competition;

import lombok.Data;

import java.util.List;

@Data
public class AcademyDTO {
    private String id;
    private String name;
    private List<String> coaches;
    private Long created;
}
