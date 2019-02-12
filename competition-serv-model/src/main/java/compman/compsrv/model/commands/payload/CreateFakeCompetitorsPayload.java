package compman.compsrv.model.commands.payload;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
@Data
@NoArgsConstructor
public class CreateFakeCompetitorsPayload implements Serializable {
    private Integer numberOfCompetitors;
    private Integer numberOfAcademies;
}
