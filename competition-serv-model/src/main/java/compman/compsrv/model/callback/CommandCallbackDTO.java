package compman.compsrv.model.callback;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

@ToString(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class CommandCallbackDTO {
    private String id;
    private String correlationId;
    private CommandExecutionResult result;
    private ErrorCallbackDTO errorInfo;
}