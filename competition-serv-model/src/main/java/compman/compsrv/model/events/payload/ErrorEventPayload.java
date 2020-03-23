package compman.compsrv.model.events.payload;

import compman.compsrv.model.commands.payload.Payload;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true
)
public class ErrorEventPayload implements Serializable, Payload {
    private String error;
    private String failedOn;
}
