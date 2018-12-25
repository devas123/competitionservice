package compman.compsrv.model.events.payload;

import compman.compsrv.model.commands.CommandDTO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class ErrorEventPayload implements Serializable {

    private String error;
    private CommandDTO failedOn;
}
