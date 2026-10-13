package iuh.fit.auditservice.application.service.impl;

import iuh.fit.auditservice.application.dto.response.system.IncidentResponseCommand;
import iuh.fit.auditservice.application.dto.response.system.InfrastructureResponseCommand;
import iuh.fit.auditservice.application.dto.response.system.JobSummaryResponseCommand;
import iuh.fit.auditservice.application.dto.response.system.ServiceHealthResponseCommand.ServiceHealthStatus;
import iuh.fit.auditservice.application.dto.response.system.ServiceHealthResponseCommand;
import iuh.fit.auditservice.application.dto.response.system.SystemHealthResponseCommand;
import iuh.fit.auditservice.application.dto.response.system.TrendResponseCommand;
import iuh.fit.auditservice.application.exception.ServiceException;
import iuh.fit.auditservice.application.port.monitoring.BackgroundJobPort;
import iuh.fit.auditservice.application.port.monitoring.InfrastructurePort;
import iuh.fit.auditservice.application.port.monitoring.MetricsQueryPort;
import iuh.fit.auditservice.application.port.notification.AlertPort;
import iuh.fit.auditservice.application.port.persistence.IncidentRepository;
import iuh.fit.auditservice.application.service.SystemHealthService;
import iuh.fit.auditservice.domain.Incident;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

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

    /** Dịch vụ ngừng lâu hơn mức này thì báo admin (ngừng thoáng qua lúc khởi động lại thì không). */
    public static final Duration DOWN_ALERT_AFTER = Duration.ofMinutes(2);
    static final String TREND = "sum by (service) (rate(" + REQUESTS.formatted("") + "[%s]))";

    private final MetricsQueryPort metrics;
    private final String grafanaUrl;
    private final Clock clock;
    private final IncidentRepository incidents;
    private final AlertPort alerts;
    private final InfrastructurePort infrastructure;
    private final BackgroundJobPort jobs;

    public SystemHealthServiceImpl(MetricsQueryPort metrics, String grafanaUrl, Clock clock) {
        this(metrics, grafanaUrl, clock, null, null, null, null);
    }

    public SystemHealthServiceImpl(MetricsQueryPort metrics, String grafanaUrl, Clock clock, IncidentRepository incidents,
                                   AlertPort alerts, InfrastructurePort infrastructure, BackgroundJobPort jobs) {
        this.metrics = metrics;
        this.grafanaUrl = grafanaUrl;
        this.clock = clock;
        this.incidents = incidents;
        this.alerts = alerts;
        this.infrastructure = infrastructure;
        this.jobs = jobs;
    }

    @Override
    public TrendResponseCommand getTrends(String range) throws ServiceException {
        Duration span;
        Duration step;
        String window;
        switch (range == null ? "" : range) {
            case "1h" -> { span = Duration.ofHours(1); step = Duration.ofMinutes(1); window = "2m"; }
            case "24h" -> { span = Duration.ofHours(24); step = Duration.ofMinutes(15); window = "15m"; }
            default -> throw new ServiceException("Khoảng xu hướng chỉ nhận 1h hoặc 24h.");
        }
        // Mốc chia hết cho bước để các điểm của mọi service thẳng hàng.
        long stepSeconds = step.toSeconds();
        Instant end = Instant.ofEpochSecond(Math.floorDiv(clock.instant().getEpochSecond(), stepSeconds) * stepSeconds);
        Instant start = end.minus(span);
        try {
            return new TrendResponseCommand(range, start, stepSeconds,
                    metrics.rangeByService(TREND.formatted(window), start, end, step));
        } catch (MetricsQueryPort.MetricsUnavailableException exception) {
            throw new ServiceException("Không đọc được Prometheus để vẽ xu hướng.");
        }
    }

    @Override
    public List<IncidentResponseCommand> getIncidents(int days) {
        Instant now = clock.instant();
        return incidents.findRecent(now.minus(Duration.ofDays(Math.max(1, Math.min(days, 90)))), 100).stream()
                .map(incident -> new IncidentResponseCommand(incident.getId(), incident.getService(), incident.getStatus(),
                        incident.getStartedAt(), incident.getEndedAt(),
                        Duration.between(incident.getStartedAt(), incident.getEndedAt() == null ? now : incident.getEndedAt()).toSeconds(),
                        incident.getAlertedAt() != null))
                .toList();
    }

    @Override
    public InfrastructureResponseCommand getInfrastructure() {
        return new InfrastructureResponseCommand(clock.instant(), infrastructure.check(), infrastructure.consumerLag());
    }

    @Override
    public List<JobSummaryResponseCommand> getJobs() {
        return jobs.summaries();
    }

    @Override
    public void trackIncidents() {
        SystemHealthResponseCommand health = getServicesHealth();
        // Prometheus không trả lời thì không biết gì về dịch vụ: giữ nguyên sự cố, không mở / đóng nhầm.
        if (!health.metricsAvailable()) return;
        Instant now = clock.instant();
        Map<String, Incident> open = new java.util.HashMap<>();
        incidents.findOpen().forEach(incident -> open.put(incident.getService(), incident));

        for (ServiceHealthResponseCommand service : health.services()) {
            Incident current = open.get(service.service());
            switch (service.status()) {
                case DOWN, DEGRADED -> {
                    if (current == null) {
                        current = incidents.save(Incident.builder().service(service.service())
                                .status(service.status().name()).startedAt(now).build());
                        open.put(service.service(), current);
                    } else if (service.status() == ServiceHealthStatus.DOWN && !"DOWN".equals(current.getStatus())) {
                        current.setStatus("DOWN");
                        incidents.save(current);
                    }
                }
                case UP -> {
                    if (current == null) continue;
                    current.setEndedAt(now);
                    incidents.save(current);
                    open.remove(service.service());
                    if (current.getAlertedAt() != null) {
                        alerts.notifyAdmin(eventId("incident-up:", current), "Dịch vụ " + service.service() + " đã hoạt động lại",
                                "Ngừng khoảng " + minutes(current.getStartedAt(), now) + " phút. Xem lịch sử sự cố ở trang Giám sát.",
                                "SYSTEM_ALERT", eventId("incident:", current));
                    }
                }
                default -> { }
            }
        }

        for (Incident incident : open.values()) {
            boolean longDown = "DOWN".equals(incident.getStatus()) && incident.getAlertedAt() == null
                    && !incident.getStartedAt().plus(DOWN_ALERT_AFTER).isAfter(now);
            if (!longDown) continue;
            alerts.notifyAdmin(eventId("incident-down:", incident), "Dịch vụ " + incident.getService() + " đang ngừng",
                    "Ngừng từ " + minutes(incident.getStartedAt(), now) + " phút trước (Prometheus không gọi được). Xem ở trang Giám sát.",
                    "SYSTEM_ALERT", eventId("incident:", incident));
            incident.setAlertedAt(now);
            incidents.save(incident);
        }
    }

    private static UUID eventId(String prefix, Incident incident) {
        return UUID.nameUUIDFromBytes((prefix + incident.getId()).getBytes(StandardCharsets.UTF_8));
    }

    private static long minutes(Instant from, Instant to) {
        return Math.max(1, Duration.between(from, to).toMinutes());
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
