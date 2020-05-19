package compman.compsrv.model.commands.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Data
public class AdjacencyList {
    private int root;
    private AdjacencyListEntry[] vertices;
}
