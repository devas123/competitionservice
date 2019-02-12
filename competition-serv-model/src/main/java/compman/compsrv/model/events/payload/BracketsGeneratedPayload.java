package compman.compsrv.model.events.payload;

import compman.compsrv.model.dto.brackets.BracketType;
import compman.compsrv.model.dto.competition.FightDescriptionDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BracketsGeneratedPayload implements Serializable {
    private FightDescriptionDTO[] fights;
    private BracketType bracketType;
}
