package iuh.fit.auditservice.application.dto.response.log;

import java.time.Instant;

public record LogResponseCommand(
    String logId,
    String userId,
    String method,
    String path,
    String queryParams,
    String ipAddress,
    int statusCode,
    long duration,
    Instant timestamp,
    String errorMessage
) {}