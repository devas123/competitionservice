package compman.compsrv.model.events.payload;

import compman.compsrv.model.dto.competition.CategoryStateDTO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class CategoryAddedPayload implements Serializable {
    private CategoryStateDTO categoryState;
}
