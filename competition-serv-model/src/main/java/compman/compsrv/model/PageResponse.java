package compman.compsrv.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public final class PageResponse<T> {
    private final String competitionId;
    private final long total;
    private final int page;
    private final T[] data;
}