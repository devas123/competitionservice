package compman.compsrv.model.commands.payload;

import lombok.Data;

import java.io.Serializable;

@Data
public class RemoveCompetitorPayload implements Serializable {
    private String competitorId;
}
