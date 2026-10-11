package iuh.fit.auditservice.application.dto.response.system;

import java.time.Instant;
import java.util.List;

public record SystemHealthResponseCommand(
        Instant checkedAt,
        boolean metricsAvailable,
        String grafanaUrl,
        List<ServiceHealthResponseCommand> services
) {}
