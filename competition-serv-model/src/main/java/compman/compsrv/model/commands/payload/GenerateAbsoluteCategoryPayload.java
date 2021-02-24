package compman.compsrv.model.commands.payload;

import compman.compsrv.annotations.CommandPayload;
import compman.compsrv.model.Payload;
import compman.compsrv.model.commands.CommandType;
import compman.compsrv.model.dto.competition.CategoryDescriptorDTO;
import compman.compsrv.model.dto.competition.CompetitorDTO;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@CommandPayload(type = CommandType.SAVE_ABSOLUTE_CATEGORY_COMMAND)
public class GenerateAbsoluteCategoryPayload implements Serializable, Payload {
    private CompetitorDTO[] competitors;
    private CategoryDescriptorDTO category;
    private String competitionId;
}
