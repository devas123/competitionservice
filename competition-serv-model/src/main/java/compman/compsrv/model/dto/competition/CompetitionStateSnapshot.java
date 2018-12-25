package compman.compsrv.model.dto.competition;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Set;

@Data
@AllArgsConstructor
public class CompetitionStateSnapshot {
    private String competitionId;
    private String processingInstanceId;
    private Integer eventPartition;
    private Long eventOffset;
    private Set<String> processedEventIds;
    private Set<String> processedCommandIds;
    private String serializedState;
}
