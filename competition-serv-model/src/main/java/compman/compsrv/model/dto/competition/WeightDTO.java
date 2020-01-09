package compman.compsrv.model.dto.competition;

import lombok.*;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@RequiredArgsConstructor
public class WeightDTO {
    public static final String ROOSTER = "Rooster";
    public static final String LIGHT_FEATHER = "LightFeather";

    public static final String FEATHER = "Feather";

    public static final String LIGHT = "Light";

    public static final String MIDDLE = "Middle";

    public static final String MEDIUM_HEAVY = "Medium Heavy";

    public static final String HEAVY = "Heavy";

    public static final String SUPER_HEAVY = "Super Heavy";

    public static final String ULTRA_HEAVY = "Ultra Heavy";

    public static final String OPEN_CLASS = "Open class";

    public static final String[] WEIGHT_NAMES = {ROOSTER, LIGHT_FEATHER, FEATHER, LIGHT, MIDDLE,
            MEDIUM_HEAVY, HEAVY, SUPER_HEAVY, ULTRA_HEAVY, OPEN_CLASS};


    @NonNull
    private String id;
    private String name;
    @NonNull
    private BigDecimal maxValue;
    private BigDecimal minValue = BigDecimal.ZERO;
}
