package compman.compsrv.model.commands.payload;

import compman.compsrv.model.dto.competition.CategoryRestrictionDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class GenerateCategoriesFromRestrictionsPayload implements Serializable, Payload {
    private CategoryRestrictionDTO[] restrictions;
    private AdjacencyList[] idTrees;
    private String[] restrictionNames;
}
