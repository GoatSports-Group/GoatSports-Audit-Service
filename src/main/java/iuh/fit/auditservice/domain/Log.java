package iuh.fit.auditservice.domain;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Log {
    private String logId;

    /** Khoá dedupe: cùng eventId chỉ được ghi một bản ghi audit. */
    private String eventId;
    private String eventType;
    private String correlationId;
    private String sourceService;
    private String actorId;
    private String resourceType;
    private String resourceId;

    private String userId;
    private String action;
    private String description;
    private String ipAddress;
    private int statusCode;
    private LocalDateTime timestamp;
    private String errorMessage;

    private AuditContext context;
    private List<AuditChange> changes;
}
