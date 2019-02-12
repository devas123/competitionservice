package compman.compsrv.model.commands.payload;

import compman.compsrv.model.dto.brackets.BracketType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class GenerateBracketsPayload implements Serializable {
    private BracketType bracketType;
}