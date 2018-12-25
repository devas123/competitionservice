package compman.compsrv.model.commands.payload;

import lombok.Data;

import java.io.Serializable;
@Data
public class CreateFakeCompetitorsPayload implements Serializable {
    private Integer numberOfCompetitors;
    private Integer numberOfAcademies;
}
