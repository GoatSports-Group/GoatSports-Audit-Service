package iuh.fit.auditservice.application.dto.response.system;

/**
 * Sức khỏe một service theo số liệu Prometheus (cửa sổ 5 phút). Số liệu null nghĩa là chưa có dữ liệu
 * (service mới khởi động hoặc chưa nhận request), không phải bằng 0.
 */
public record ServiceHealthResponseCommand(
        String service,
        ServiceHealthStatus status,
        Double requestsPerSecond,
        /** Tỉ lệ 0–1 của request trả 5xx. */
        Double errorRate,
        Double p95LatencyMs,
        /** JVM: tỉ lệ 0–1 CPU của máy mà process dùng; ai-service (Python): số lõi đang dùng. */
        Double cpuUsage,
        /** JVM: heap đang dùng; ai-service: bộ nhớ thường trú (RSS). */
        Double memoryUsedBytes,
        Double uptimeSeconds
) {
    public enum ServiceHealthStatus { UP, DEGRADED, DOWN, UNKNOWN }
}
