package compman.compsrv.model.commands.payload;

import compman.compsrv.annotations.CommandPayload;
import compman.compsrv.model.Payload;
import compman.compsrv.model.commands.CommandType;
import compman.compsrv.model.dto.brackets.StageDescriptorDTO;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@NoArgsConstructor
@Accessors(chain = true)
@CommandPayload(type = CommandType.GENERATE_BRACKETS_COMMAND)
public class GenerateBracketsPayload implements Serializable, Payload {
    private StageDescriptorDTO[] stageDescriptors;
}
