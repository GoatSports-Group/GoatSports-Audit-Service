package iuh.fit.auditservice.domain;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Log {
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
