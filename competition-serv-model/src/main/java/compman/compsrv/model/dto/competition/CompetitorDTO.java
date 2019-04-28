package compman.compsrv.model.dto.competition;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Date;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorDTO {
    private String id;
    private String email;
    private String userId;
    private String firstName;
    private String lastName;
    private Instant birthDate;
    private String academy;
    private String categoryId;
    private String competitionId;
    private String registrationStatus;
    private String promo;
}
