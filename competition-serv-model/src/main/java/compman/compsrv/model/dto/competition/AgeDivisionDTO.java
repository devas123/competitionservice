package compman.compsrv.model.dto.competition;

import lombok.*;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class AgeDivisionDTO {
    public static final AgeDivisionDTO MIGHTY_MITE_I = new AgeDivisionDTO().setName("MIGHTY MITE I").setMinimalAge(4).setMaximalAge(4);
    public static final AgeDivisionDTO MIGHTY_MITE_II = new AgeDivisionDTO().setName("MIGHTY MITE II").setMinimalAge(5).setMaximalAge(5);
    public static final AgeDivisionDTO MIGHTY_MITE_III = new AgeDivisionDTO().setName("MIGHTY MITE III").setMinimalAge(6).setMaximalAge(6);
    public static final AgeDivisionDTO PEE_WEE_I = new AgeDivisionDTO().setName("PEE WEE I").setMinimalAge(7).setMaximalAge(7);
    public static final AgeDivisionDTO PEE_WEE_II = new AgeDivisionDTO().setName("PEE WEE II").setMinimalAge(8).setMaximalAge(8);
    public static final AgeDivisionDTO PEE_WEE_III = new AgeDivisionDTO().setName("PEE WEE III").setMinimalAge(9).setMaximalAge(9);
    public static final AgeDivisionDTO JUNIOR_I = new AgeDivisionDTO().setName("JUNIOR I").setMinimalAge(10).setMaximalAge(10);
    public static final AgeDivisionDTO JUNIOR_II = new AgeDivisionDTO().setName("JUNIOR II").setMinimalAge(11).setMaximalAge(11);
    public static final AgeDivisionDTO JUNIOR_III = new AgeDivisionDTO().setName("JUNIOR III").setMinimalAge(12).setMaximalAge(12);
    public static final AgeDivisionDTO TEEN_I = new AgeDivisionDTO().setName("TEEN I").setMinimalAge(13).setMaximalAge(13);
    public static final AgeDivisionDTO TEEN_II = new AgeDivisionDTO().setName("TEEN II").setMinimalAge(14).setMaximalAge(14);
    public static final AgeDivisionDTO TEEN_III = new AgeDivisionDTO().setName("TEEN III").setMinimalAge(15).setMaximalAge(15);
    public static final AgeDivisionDTO JUVENILE_I = new AgeDivisionDTO().setName("JUVENILE I").setMinimalAge(16).setMaximalAge(16);
    public static final AgeDivisionDTO JUVENILE_II = new AgeDivisionDTO().setName("JUVENILE II").setMinimalAge(17).setMaximalAge(17);
    public static final AgeDivisionDTO ADULT = new AgeDivisionDTO().setName("ADULT").setMinimalAge(18).setMaximalAge(100);
    public static final AgeDivisionDTO MASTER_1 = new AgeDivisionDTO().setName("MASTER 1").setMinimalAge(30).setMaximalAge(100);
    public static final AgeDivisionDTO MASTER_2 = new AgeDivisionDTO().setName("MASTER 2").setMinimalAge(35).setMaximalAge(100);
    public static final AgeDivisionDTO MASTER_3 = new AgeDivisionDTO().setName("MASTER 3").setMinimalAge(40).setMaximalAge(100);
    public static final AgeDivisionDTO MASTER_4 = new AgeDivisionDTO().setName("MASTER 4").setMinimalAge(45).setMaximalAge(100);
    public static final AgeDivisionDTO MASTER_5 = new AgeDivisionDTO().setName("MASTER 5").setMinimalAge(50).setMaximalAge(100);
    public static final AgeDivisionDTO MASTER_6 = new AgeDivisionDTO().setName("MASTER 6").setMinimalAge(55).setMaximalAge(100);
    private String id;
    private String name;
    private Integer minimalAge;
    private Integer maximalAge;
}
