package iuh.fit.auditservice.infrastructure.external.prometheus;

import iuh.fit.auditservice.application.port.monitoring.MetricsQueryPort;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrometheusMetricsAdapter implements MetricsQueryPort {
    private final RestClient restClient;

    public PrometheusMetricsAdapter(String prometheusUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder().baseUrl(prometheusUrl).requestFactory(requestFactory).build();
    }

    record QueryResponse(String status, Data data) {}

    record Data(List<Sample> result) {}

    /** value = [thời điểm, "giá trị dạng chuỗi"]; truy vấn khoảng trả values = danh sách các cặp như vậy. */
    record Sample(Map<String, String> metric, List<Object> value, List<List<Object>> values) {}

    @Override
    public Map<String, Double> queryByService(String promQl) {
        QueryResponse response;
        try {
            response = restClient.get()
                    .uri(uri -> uri.path("/api/v1/query").queryParam("query", "{query}").build(promQl))
                    .retrieve()
                    .body(QueryResponse.class);
        } catch (RestClientException exception) {
            throw new MetricsUnavailableException("Không đọc được Prometheus", exception);
        }
        if (response == null || !"success".equals(response.status()) || response.data() == null) {
            throw new MetricsUnavailableException("Prometheus trả kết quả không hợp lệ", null);
        }
        return toServiceValues(response.data().result());
    }

    @Override
    public Map<String, List<Double>> rangeByService(String promQl, Instant start, Instant end, Duration step) {
        QueryResponse response;
        try {
            response = restClient.get()
                    .uri(uri -> uri.path("/api/v1/query_range").queryParam("query", "{query}")
                            .queryParam("start", start.getEpochSecond()).queryParam("end", end.getEpochSecond())
                            .queryParam("step", step.toSeconds()).build(promQl))
                    .retrieve()
                    .body(QueryResponse.class);
        } catch (RestClientException exception) {
            throw new MetricsUnavailableException("Không đọc được Prometheus", exception);
        }
        if (response == null || !"success".equals(response.status()) || response.data() == null) {
            throw new MetricsUnavailableException("Prometheus trả kết quả không hợp lệ", null);
        }
        return toServiceSeries(response.data().result(), start, step,
                (int) (Duration.between(start, end).toSeconds() / step.toSeconds()) + 1);
    }

    /** Xếp các điểm vào đúng ô theo thời điểm (Prometheus bỏ qua điểm không có số liệu, không trả null). */
    static Map<String, List<Double>> toServiceSeries(List<Sample> samples, Instant start, Duration step, int points) {
        Map<String, List<Double>> series = new HashMap<>();
        if (samples == null) return series;
        for (Sample sample : samples) {
            String service = sample.metric() == null ? null : sample.metric().get("service");
            if (service == null || sample.values() == null) continue;
            List<Double> values = new ArrayList<>(Collections.nCopies(points, (Double) null));
            for (List<Object> point : sample.values()) {
                if (point == null || point.size() < 2) continue;
                long at = Math.round(Double.parseDouble(String.valueOf(point.get(0))));
                int index = (int) ((at - start.getEpochSecond()) / step.toSeconds());
                double value = Double.parseDouble(String.valueOf(point.get(1)));
                if (index >= 0 && index < points && !Double.isNaN(value) && !Double.isInfinite(value)) values.set(index, value);
            }
            series.put(service, values);
        }
        return series;
    }

    static Map<String, Double> toServiceValues(List<Sample> samples) {
        Map<String, Double> values = new HashMap<>();
        if (samples == null) return values;
        for (Sample sample : samples) {
            String service = sample.metric() == null ? null : sample.metric().get("service");
            if (service == null || sample.value() == null || sample.value().size() < 2) continue;
            double value = Double.parseDouble(String.valueOf(sample.value().get(1)));
            if (!Double.isNaN(value) && !Double.isInfinite(value)) values.put(service, value);
        }
        return values;
    }
}
