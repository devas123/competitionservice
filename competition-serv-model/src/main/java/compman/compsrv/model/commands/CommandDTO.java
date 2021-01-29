package compman.compsrv.model.commands;

import compman.compsrv.model.events.MessageInfo;
import lombok.*;
import lombok.experimental.Accessors;

import java.util.LinkedHashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class CommandDTO  extends MessageInfo {
    private CommandType type;
    private Boolean executed = false;
}
