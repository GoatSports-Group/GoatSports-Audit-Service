package iuh.fit.auditservice.presentation.response.log;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogResponse {
    private String logId;
    private String userId;
    private String action;
    private String description;
    private String ipAddress;
    private int statusCode;
    private LocalDateTime timestamp;
    private String errorMessage;
    private String sourceService;
    private String eventType;
    private String correlationId;
    private String resourceType;
    private String resourceId;
    private String httpMethod;
    private String requestUri;
    private String userAgent;
}
