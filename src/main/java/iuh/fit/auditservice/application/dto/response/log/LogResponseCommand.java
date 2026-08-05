package iuh.fit.auditservice.application.dto.response.log;

import java.time.LocalDateTime;

public record LogResponseCommand(
    String logId,
    String userId,
    String action,
    String description,
    String ipAddress,
    int statusCode,
    LocalDateTime timestamp,
    String errorMessage
) {}