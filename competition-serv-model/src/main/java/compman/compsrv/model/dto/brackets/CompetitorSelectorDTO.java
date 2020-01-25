package compman.compsrv.model.dto.brackets;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class CompetitorSelectorDTO {
    private String id;
    private String applyToStageId;
    private LogicalOperator logicalOperator;
    private SelectorClassifier classifier;
    private OperatorType operator;
    private String[] selectorValue;
}
