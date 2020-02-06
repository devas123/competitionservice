package compman.compsrv.model.dto.competition;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@Builder(toBuilder = true)
public class CompetitorDTO {
    private String id;
    private String email;
    private String userId;
    private String firstName;
    private String lastName;
    private Instant birthDate;
    private AcademyDTO academy;
    private String[] categories;
    private String competitionId;
    private String registrationStatus;
    private String promo;
}
