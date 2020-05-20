package compman.compsrv.model.commands.payload;

import compman.compsrv.model.dto.brackets.StageDescriptorDTO;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class GenerateBracketsPayload implements Serializable, Payload {
    private StageDescriptorDTO[] stageDescriptors;
}
