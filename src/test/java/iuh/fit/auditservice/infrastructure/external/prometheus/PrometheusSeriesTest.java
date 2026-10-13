package iuh.fit.auditservice.infrastructure.external.prometheus;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusSeriesTest {

    @Test
    void placesEachPointInItsTimeSlotAndLeavesGapsNull() {
        Instant start = Instant.parse("2026-10-14T03:00:00Z");
        long t = start.getEpochSecond();
        PrometheusMetricsAdapter.Sample sample = new PrometheusMetricsAdapter.Sample(Map.of("service", "venue-service"), null,
                List.of(List.of(t, "1.5"), List.of(t + 120, "NaN"), List.of(t + 180, "3")));

        Map<String, List<Double>> series = PrometheusMetricsAdapter.toServiceSeries(List.of(sample), start, Duration.ofMinutes(1), 4);

        assertThat(series.get("venue-service")).isEqualTo(Arrays.asList(1.5, null, null, 3.0));
    }
}
