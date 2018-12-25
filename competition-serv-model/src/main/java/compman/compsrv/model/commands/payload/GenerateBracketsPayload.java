package compman.compsrv.model.commands.payload;

import compman.compsrv.model.dto.brackets.BracketType;
import lombok.Data;

import java.io.Serializable;

@Data
public class GenerateBracketsPayload implements Serializable {
    private BracketType bracketType;
}
