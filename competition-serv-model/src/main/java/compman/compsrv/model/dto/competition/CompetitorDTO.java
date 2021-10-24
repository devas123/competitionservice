package compman.compsrv.model.dto.competition;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;

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
    private AcademyDTO academy;
    private String[] categories;
    private String competitionId;
    private CompetitorRegistrationStatus registrationStatus;
    private boolean placeholder;
    private String promo;
}
