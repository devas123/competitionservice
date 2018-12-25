package compman.compsrv.model.dto.competition;

import lombok.Data;

import java.time.ZonedDateTime;
import java.util.List;

@Data
public class CompetitionPropertiesDTO {
    private String id;
    private String creatorId;
    private String[] staffIds;
    private Boolean emailNotificationsEnabled;
    private String competitionName;
    private String emailTemplate;
    private List<PromoCodeDTO> promoCodes;
    private ZonedDateTime startDate;
    private Boolean schedulePublished;
    private Boolean bracketsPublished;
    private ZonedDateTime endDate;
    private String timeZone;
    private RegistrationInfoDTO registrationInfo;
}
