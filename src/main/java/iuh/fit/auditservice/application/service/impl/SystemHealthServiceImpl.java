package iuh.fit.auditservice.application.service.impl;

import iuh.fit.auditservice.application.dto.response.system.ServiceHealthResponseCommand;
import iuh.fit.auditservice.application.dto.response.system.ServiceHealthResponseCommand.ServiceHealthStatus;
import iuh.fit.auditservice.application.dto.response.system.SystemHealthResponseCommand;
import iuh.fit.auditservice.application.port.monitoring.MetricsQueryPort;
import iuh.fit.auditservice.application.service.SystemHealthService;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Tổng hợp sức khỏe từng service từ Prometheus: mỗi chỉ số là MỘT truy vấn gộp theo nhãn {@code service}
 * (7 truy vấn cho cả hệ thống), không hỏi riêng từng service.
 */
public class SystemHealthServiceImpl implements SystemHealthService {
    // Bỏ request Prometheus tự gọi để scrape, nếu không service rảnh vẫn hiện ~0,07 request/giây.
    private static final String REQUESTS = "http_server_requests_seconds_count{job=\"goatsports\",uri!~\"/actuator.*\"%s}";
    static final String UP = "up{job=\"goatsports\"}";
    static final String REQUEST_RATE = "sum by (service) (rate(" + REQUESTS.formatted("") + "[5m]))";
    static final String ERROR_RATE = "sum by (service) (rate(" + REQUESTS.formatted(",status=~\"5..\"") + "[5m]))";
    static final String P95 = "histogram_quantile(0.95, sum by (le, service) (rate(http_server_requests_seconds_bucket"
            + "{job=\"goatsports\",uri!~\"/actuator.*\"}[5m])))";
    // JVM có process_cpu_usage / heap; ai-service (Python) chỉ có process_cpu_seconds_total / RSS.
    static final String CPU = "max by (service) (process_cpu_usage{job=\"goatsports\"})"
            + " or sum by (service) (rate(process_cpu_seconds_total{job=\"goatsports\"}[5m]))";
    static final String MEMORY = "sum by (service) (jvm_memory_used_bytes{job=\"goatsports\",area=\"heap\"})"
            + " or sum by (service) (process_resident_memory_bytes{job=\"goatsports\"})";
    static final String UPTIME = "max by (service) (process_uptime_seconds{job=\"goatsports\"})";

    /** Chậm/lỗi tới mức người dùng thấy: 5% request lỗi (khi có tải thật) hoặc p95 quá 1 giây. */
    static final double DEGRADED_ERROR_RATE = 0.05;
    static final double MIN_RATE_FOR_ERROR_RATIO = 0.05;
    static final double DEGRADED_P95_MS = 1000;

    private final MetricsQueryPort metrics;
    private final String grafanaUrl;
    private final Clock clock;

    public SystemHealthServiceImpl(MetricsQueryPort metrics, String grafanaUrl, Clock clock) {
        this.metrics = metrics;
        this.grafanaUrl = grafanaUrl;
        this.clock = clock;
    }

    @Override
    public SystemHealthResponseCommand getServicesHealth() {
        Map<String, Double> up;
        Map<String, Double> rate;
        Map<String, Double> errors;
        Map<String, Double> p95;
        Map<String, Double> cpu;
        Map<String, Double> memory;
        Map<String, Double> uptime;
        try {
            up = metrics.queryByService(UP);
            rate = metrics.queryByService(REQUEST_RATE);
            errors = metrics.queryByService(ERROR_RATE);
            p95 = metrics.queryByService(P95);
            cpu = metrics.queryByService(CPU);
            memory = metrics.queryByService(MEMORY);
            uptime = metrics.queryByService(UPTIME);
        } catch (MetricsQueryPort.MetricsUnavailableException exception) {
            return new SystemHealthResponseCommand(clock.instant(), false, grafanaUrl, List.of());
        }

        List<ServiceHealthResponseCommand> services = new TreeSet<>(up.keySet()).stream()
                .map(service -> {
                    Double requestRate = rate.get(service);
                    Double errorRate = ratio(errors.getOrDefault(service, 0.0), requestRate);
                    Double latencyMs = p95.containsKey(service) ? p95.get(service) * 1000 : null;
                    return new ServiceHealthResponseCommand(
                            service, status(up.get(service), requestRate, errorRate, latencyMs),
                            requestRate, errorRate, latencyMs,
                            cpu.get(service), memory.get(service), uptime.get(service));
                })
                .sorted(Comparator.comparing(ServiceHealthResponseCommand::service))
                .toList();
        return new SystemHealthResponseCommand(clock.instant(), true, grafanaUrl, services);
    }

    static ServiceHealthStatus status(Double up, Double requestRate, Double errorRate, Double p95Ms) {
        if (up == null) return ServiceHealthStatus.UNKNOWN;
        if (up < 1) return ServiceHealthStatus.DOWN;
        boolean failing = errorRate != null && requestRate != null && requestRate >= MIN_RATE_FOR_ERROR_RATIO
                && errorRate >= DEGRADED_ERROR_RATE;
        boolean slow = p95Ms != null && p95Ms >= DEGRADED_P95_MS;
        return failing || slow ? ServiceHealthStatus.DEGRADED : ServiceHealthStatus.UP;
    }

    private static Double ratio(double part, Double total) {
        return total == null || total <= 0 ? null : part / total;
    }
}
