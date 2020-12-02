package compman.compsrv.model.commands;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class CommandDTO {
    private String id;
    private String correlationId;
    private String competitionId;
    private CommandType type;
    private String categoryId;
    private String matId;
    private String competitorId;
    private LinkedHashMap<String, Object> payload;
    private Map<String, String> metadata;
    private Boolean executed = false;
}
