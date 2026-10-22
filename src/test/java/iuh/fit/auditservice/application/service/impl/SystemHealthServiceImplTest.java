package iuh.fit.auditservice.application.service.impl;

import iuh.fit.auditservice.application.dto.response.system.ServiceHealthResponseCommand;
import iuh.fit.auditservice.application.dto.response.system.ServiceHealthResponseCommand.ServiceHealthStatus;
import iuh.fit.auditservice.application.dto.response.system.SystemHealthResponseCommand;
import iuh.fit.auditservice.application.port.monitoring.MetricsQueryPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SystemHealthServiceImplTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T03:00:00Z"), ZoneOffset.UTC);

    /** Trả số liệu theo từ khóa trong câu truy vấn, đồng thời ghi lại các câu đã chạy. */
    private static class FakeMetrics implements MetricsQueryPort {
        final List<String> queries = new ArrayList<>();

        @Override
        public Map<String, Double> queryByService(String promQl) {
            queries.add(promQl);
            if (promQl.startsWith("up{")) return Map.of("venue-service", 1.0, "payment-service", 1.0, "social-service", 0.0);
            if (promQl.contains("status=~\"5..\"")) return Map.of("payment-service", 0.5);
            if (promQl.startsWith("sum by (service) (rate(http")) return Map.of("venue-service", 4.0, "payment-service", 2.0);
            if (promQl.startsWith("histogram_quantile")) return Map.of("venue-service", 0.12, "payment-service", 0.2);
            if (promQl.contains("process_uptime_seconds")) return Map.of("venue-service", 3600.0);
            return Map.of();
        }

        @Override
        public Map<String, List<Double>> rangeByService(String promQl, Instant start, Instant end, Duration step) {
            queries.add(promQl);
            return Map.of();
        }
    }

    @Test
    void classifiesEachServiceFromOneAggregatedQueryPerMetric() {
        FakeMetrics metrics = new FakeMetrics();

        SystemHealthResponseCommand health = new SystemHealthServiceImpl(metrics, "http://grafana", CLOCK).getServicesHealth();

        // 7 chỉ số = 7 truy vấn cho mọi service, không phải 7 truy vấn mỗi service.
        assertThat(metrics.queries).hasSize(7);
        assertThat(health.metricsAvailable()).isTrue();
        assertThat(health.checkedAt()).isEqualTo(CLOCK.instant());
        assertThat(health.services()).extracting(ServiceHealthResponseCommand::service)
                .containsExactly("payment-service", "social-service", "venue-service");

        ServiceHealthResponseCommand payment = health.services().get(0);
        assertThat(payment.status()).isEqualTo(ServiceHealthStatus.DEGRADED);
        assertThat(payment.errorRate()).isEqualTo(0.25);

        ServiceHealthResponseCommand venue = health.services().get(2);
        assertThat(venue.status()).isEqualTo(ServiceHealthStatus.UP);
        assertThat(venue.p95LatencyMs()).isEqualTo(120.0);
        assertThat(venue.errorRate()).isZero();
        assertThat(venue.uptimeSeconds()).isEqualTo(3600.0);

        assertThat(health.services().get(1).status()).isEqualTo(ServiceHealthStatus.DOWN);
    }

    @Test
    void lowTrafficErrorsAndMissingTargetsAreNotReportedAsOutages() {
        // Một lỗi trong lúc gần như không có tải không đủ để báo suy giảm.
        assertThat(SystemHealthServiceImpl.status(1.0, 0.01, 1.0, 50.0)).isEqualTo(ServiceHealthStatus.UP);
        assertThat(SystemHealthServiceImpl.status(1.0, 1.0, 0.0, 1500.0)).isEqualTo(ServiceHealthStatus.DEGRADED);
        assertThat(SystemHealthServiceImpl.status(null, null, null, null)).isEqualTo(ServiceHealthStatus.UNKNOWN);
    }

    @Test
    void unreachablePrometheusIsReportedSeparatelyFromServicesBeingDown() {
        MetricsQueryPort unreachable = new FakeMetrics() {
            @Override
            public Map<String, Double> queryByService(String promQl) {
                throw new MetricsQueryPort.MetricsUnavailableException("down", null);
            }
        };

        SystemHealthResponseCommand health = new SystemHealthServiceImpl(unreachable, null, CLOCK).getServicesHealth();

        assertThat(health.metricsAvailable()).isFalse();
        assertThat(health.services()).isEmpty();
    }
}
