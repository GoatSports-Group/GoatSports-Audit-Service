package iuh.fit.auditservice.presentation.response.log;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogResponse {
    private String logId;
    private String userId;
    private String method;
    private String path;
    private String queryParams;
    private String ipAddress;
    private int statusCode;
    private long duration;
    private Instant timestamp;
    private String errorMessage;
}
