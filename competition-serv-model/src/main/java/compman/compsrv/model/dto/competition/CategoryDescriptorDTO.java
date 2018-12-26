package compman.compsrv.model.dto.competition;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@Accessors(chain = true)
public class CategoryDescriptorDTO {
    private String sportsType;
    private AgeDivisionDTO ageDivision;
    private String gender;
    private WeightDTO weight;
    private String beltType;
    private String id;
    private BigDecimal fightDuration;
}
