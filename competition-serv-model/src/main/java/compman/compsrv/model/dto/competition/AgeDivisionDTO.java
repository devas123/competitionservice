package compman.compsrv.model.dto.competition;

import lombok.*;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
public class AgeDivisionDTO {
    public static final AgeDivisionDTO MIGHTY_MITE_I = new AgeDivisionDTO("MIGHTY MITE I", 4, 4);
    public static final AgeDivisionDTO MIGHTY_MITE_II = new AgeDivisionDTO("MIGHTY MITE II", 5, 5);
    public static final AgeDivisionDTO MIGHTY_MITE_III = new AgeDivisionDTO("MIGHTY MITE III", 6, 6);
    public static final AgeDivisionDTO PEE_WEE_I = new AgeDivisionDTO("PEE WEE I", 7, 7);
    public static final AgeDivisionDTO PEE_WEE_II = new AgeDivisionDTO("PEE WEE II", 8, 8);
    public static final AgeDivisionDTO PEE_WEE_III = new AgeDivisionDTO("PEE WEE III", 9, 9);
    public static final AgeDivisionDTO JUNIOR_I = new AgeDivisionDTO("JUNIOR I", 10, 10);
    public static final AgeDivisionDTO JUNIOR_II = new AgeDivisionDTO("JUNIOR II", 11, 11);
    public static final AgeDivisionDTO JUNIOR_III = new AgeDivisionDTO("JUNIOR III", 12, 12);
    public static final AgeDivisionDTO TEEN_I = new AgeDivisionDTO("TEEN I", 13, 13);
    public static final AgeDivisionDTO TEEN_II = new AgeDivisionDTO("TEEN II", 14, 14);
    public static final AgeDivisionDTO TEEN_III = new AgeDivisionDTO("TEEN III", 15, 15);
    public static final AgeDivisionDTO JUVENILE_I = new AgeDivisionDTO("JUVENILE I", 16, 16);
    public static final AgeDivisionDTO JUVENILE_II = new AgeDivisionDTO("JUVENILE II", 17, 17);
    public static final AgeDivisionDTO ADULT = new AgeDivisionDTO("ADULT", 18, 100);
    public static final AgeDivisionDTO MASTER_1 = new AgeDivisionDTO("MASTER 1", 30, 100);
    public static final AgeDivisionDTO MASTER_2 = new AgeDivisionDTO("MASTER 2", 36, 100);
    public static final AgeDivisionDTO MASTER_3 = new AgeDivisionDTO("MASTER 3", 41, 100);
    public static final AgeDivisionDTO MASTER_4 = new AgeDivisionDTO("MASTER 4", 46, 100);
    public static final AgeDivisionDTO MASTER_5 = new AgeDivisionDTO("MASTER 5", 51, 100);
    public static final AgeDivisionDTO MASTER_6 = new AgeDivisionDTO("MASTER 6", 56, 100);
    private String id;
    private Integer minimalAge;
    private Integer maximalAge;
}
