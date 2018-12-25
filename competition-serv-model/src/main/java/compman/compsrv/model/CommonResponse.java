package compman.compsrv.model;

import lombok.Data;

@Data
public final class CommonResponse {
    private final int status;
    private final String statusText;
    private final byte[] payload;
}
