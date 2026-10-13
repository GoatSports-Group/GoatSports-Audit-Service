package iuh.fit.auditservice.application.service.impl;

import iuh.fit.auditservice.application.dto.response.log.FailedLoginBurstResponseCommand;
import iuh.fit.auditservice.application.exception.ServiceException;
import iuh.fit.auditservice.application.port.monitoring.MetricsQueryPort;
import iuh.fit.auditservice.application.port.notification.AlertPort;
import iuh.fit.auditservice.application.port.persistence.IncidentRepository;
import iuh.fit.auditservice.application.port.persistence.LogRepository;
import iuh.fit.auditservice.domain.Incident;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonitoringFeaturesTest {
    /** Đồng hồ chỉnh được để giả lập "2 phút sau". */
    private static final class MovableClock extends Clock {
        Instant now = Instant.parse("2026-10-14T03:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    /** Trạng thái `up` của payment-service chỉnh được; Prometheus có thể "mất". */
    private static final class FakeMetrics implements MetricsQueryPort {
        double paymentUp = 1;
        boolean available = true;
        @Override public Map<String, Double> queryByService(String promQl) {
            if (!available) throw new MetricsUnavailableException("down", null);
            return promQl.startsWith("up{") ? Map.of("payment-service", paymentUp) : Map.of();
        }
        @Override public Map<String, List<Double>> rangeByService(String promQl, Instant start, Instant end, Duration step) {
            return Map.of("payment-service", List.of(1.0));
        }
    }

    private static final class MemoryIncidents implements IncidentRepository {
        final Map<String, Incident> rows = new HashMap<>();
        @Override public List<Incident> findOpen() {
            return rows.values().stream().filter(row -> row.getEndedAt() == null).toList();
        }
        @Override public Incident save(Incident incident) {
            if (incident.getId() == null) incident.setId(UUID.randomUUID().toString());
            rows.put(incident.getId(), incident);
            return incident;
        }
        @Override public List<Incident> findRecent(Instant since, int limit) { return new ArrayList<>(rows.values()); }
    }

    private record Alert(UUID eventId, String title, String referenceType) {}

    private final MovableClock clock = new MovableClock();
    private final FakeMetrics metrics = new FakeMetrics();
    private final MemoryIncidents incidents = new MemoryIncidents();
    private final List<Alert> alerts = new ArrayList<>();
    private final AlertPort alertPort = (eventId, title, content, type, ref) -> alerts.add(new Alert(eventId, title, type));
    private final SystemHealthServiceImpl service =
            new SystemHealthServiceImpl(metrics, null, clock, incidents, alertPort, null, null);

    @Test
    void opensAnIncidentAlertsOnlyAfterTwoMinutesDownAndClosesItOnRecovery() {
        metrics.paymentUp = 0;
        service.trackIncidents();
        assertThat(incidents.findOpen()).singleElement().satisfies(open -> assertThat(open.getStatus()).isEqualTo("DOWN"));
        assertThat(alerts).isEmpty();

        clock.now = clock.now.plusSeconds(90);
        service.trackIncidents();
        assertThat(alerts).isEmpty();

        clock.now = clock.now.plusSeconds(40);
        service.trackIncidents();
        service.trackIncidents();
        assertThat(alerts).singleElement().satisfies(alert -> {
            assertThat(alert.title()).contains("payment-service").contains("đang ngừng");
            assertThat(alert.referenceType()).isEqualTo("SYSTEM_ALERT");
        });

        metrics.paymentUp = 1;
        clock.now = clock.now.plusSeconds(60);
        service.trackIncidents();
        assertThat(incidents.findOpen()).isEmpty();
        assertThat(alerts).hasSize(2);
        assertThat(alerts.get(1).title()).contains("hoạt động lại");
        assertThat(service.getIncidents(7)).singleElement().satisfies(row -> {
            assertThat(row.durationSeconds()).isEqualTo(190);
            assertThat(row.alerted()).isTrue();
        });
    }

    @Test
    void keepsIncidentsUntouchedWhilePrometheusIsUnreachable() {
        metrics.paymentUp = 0;
        service.trackIncidents();
        metrics.available = false;
        clock.now = clock.now.plusSeconds(600);

        service.trackIncidents();

        assertThat(incidents.findOpen()).hasSize(1);
        assertThat(alerts).isEmpty();
    }

    @Test
    void trendsAcceptOnlyOneHourOrOneDayAndAlignToTheStep() throws ServiceException {
        clock.now = Instant.parse("2026-10-14T03:07:42Z");

        var trend = service.getTrends("24h");

        assertThat(trend.stepSeconds()).isEqualTo(900);
        assertThat(trend.start()).isEqualTo(Instant.parse("2026-10-13T03:00:00Z"));
        assertThatThrownBy(() -> service.getTrends("7d")).isInstanceOf(ServiceException.class);
    }

    @Test
    void failedLoginAlertsOncePerAccountPerWindowAndPurgesByRetention() {
        LogRepository repository = mock(LogRepository.class);
        when(repository.failedLoginBursts(any(), anyInt())).thenReturn(List.of(
                new FailedLoginBurstResponseCommand("attacker@x.vn", 7, LocalDateTime.now(), LocalDateTime.now(), "1.2.3.4")));
        LogServiceImpl logs = new LogServiceImpl(repository, alertPort, clock, 30, 15, 5);

        logs.alertFailedLogins();
        clock.now = clock.now.plusSeconds(60);
        logs.alertFailedLogins();
        clock.now = clock.now.plusSeconds(15 * 60);
        logs.alertFailedLogins();

        assertThat(alerts).hasSize(3);
        // Hai lượt cùng cửa sổ 15 phút dùng chung eventId (notification-service chỉ tạo một), cửa sổ sau thì khác.
        assertThat(alerts.get(0).eventId()).isEqualTo(alerts.get(1).eventId());
        assertThat(alerts.get(2).eventId()).isNotEqualTo(alerts.get(0).eventId());
        assertThat(alerts.get(0).referenceType()).isEqualTo("SECURITY_ALERT");

        logs.purgeExpired();
        verify(repository).deleteOlderThan(LocalDateTime.now(clock).minusDays(30));
    }
}
