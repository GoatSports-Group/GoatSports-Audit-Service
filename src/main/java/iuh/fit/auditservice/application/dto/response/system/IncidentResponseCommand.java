package iuh.fit.auditservice.application.dto.response.system;

import java.time.Instant;

/** Một lần dịch vụ ngừng / suy giảm; {@code endedAt} null là sự cố còn đang diễn ra. */
public record IncidentResponseCommand(
        String id,
        String service,
        String status,
        Instant startedAt,
        Instant endedAt,
        long durationSeconds,
        boolean alerted
) {
}
