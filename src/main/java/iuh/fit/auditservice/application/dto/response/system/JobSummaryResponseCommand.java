package iuh.fit.auditservice.application.dto.response.system;

import java.time.Instant;

/**
 * Tình trạng một loại job nền ở service sở hữu nó. {@code available = false} khi không gọi được service đó
 * (khi ấy các số còn lại là 0 và {@code error} ghi lý do).
 */
public record JobSummaryResponseCommand(
        String service,
        String job,
        String label,
        long pending,
        long running,
        long failedLast24h,
        Instant oldestPendingAt,
        boolean available,
        String error
) {
}
