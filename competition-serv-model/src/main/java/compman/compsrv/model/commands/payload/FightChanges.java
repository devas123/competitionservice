package compman.compsrv.model.commands.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class FightChanges implements Serializable {
    private JsonPatch[] changePatches;
    private JsonPatch[] changeInversePatches;
    private String[] selectedFightIds;
    private String id;
}
