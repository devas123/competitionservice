package compman.compsrv.model.dto.schedule;

import lombok.Data;

import java.time.ZonedDateTime;

@Data
public class DashboardPeriodDTO {
    private String id;
    private String name;
    private String[] matIds;
    private ZonedDateTime startTime;
    private Boolean isActive;
}
