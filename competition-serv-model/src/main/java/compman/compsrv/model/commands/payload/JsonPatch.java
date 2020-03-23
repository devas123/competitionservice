package compman.compsrv.model.commands.payload;

import compman.compsrv.model.dto.competition.CompScoreDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class JsonPatch implements Serializable {
    private String op;
    private String[] path;
    private CompScoreDTO value;
}
