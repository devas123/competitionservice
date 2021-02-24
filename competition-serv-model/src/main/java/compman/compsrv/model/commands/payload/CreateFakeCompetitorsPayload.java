package compman.compsrv.model.commands.payload;

import compman.compsrv.annotations.CommandPayload;
import compman.compsrv.model.Payload;
import compman.compsrv.model.commands.CommandType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
@Data
@NoArgsConstructor
@CommandPayload(type = CommandType.CREATE_FAKE_COMPETITORS_COMMAND)
public class CreateFakeCompetitorsPayload implements Serializable, Payload {
    private Integer numberOfCompetitors;
    private Integer numberOfAcademies;
}
