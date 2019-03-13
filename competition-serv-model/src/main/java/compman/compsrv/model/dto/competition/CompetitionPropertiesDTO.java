package compman.compsrv.model.dto.competition;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class CompetitionPropertiesDTO {
    private String id;
    private String creatorId;
    private String[] staffIds;
    private Boolean emailNotificationsEnabled;
    private String competitionName;
    private String emailTemplate;
    private List<PromoCodeDTO> promoCodes;
    private Instant startDate;
    private Boolean schedulePublished;
    private Boolean bracketsPublished;
    private Instant endDate;
    private String timeZone;
    private RegistrationInfoDTO registrationInfo;
}
