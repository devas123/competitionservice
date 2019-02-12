package compman.compsrv.model.events.payload;

import compman.compsrv.model.dto.competition.CategoryStateDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CategoryAddedPayload implements Serializable {
    private CategoryStateDTO categoryState;
}
