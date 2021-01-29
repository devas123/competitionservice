package compman.compsrv.model.events;

import compman.compsrv.model.commands.payload.Payload;
import lombok.Data;

import java.util.Map;

@Data
public class MessageInfo {
    private String id = null;
    private String correlationId;
    private String competitionId;
    private String competitorId;
    private String categoryId;
    private String matId;
    private Payload payload;
    private Map<String, String> metadata;
}
