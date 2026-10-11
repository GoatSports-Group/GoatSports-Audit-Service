package iuh.fit.auditservice.infrastructure.external.prometheus;

import iuh.fit.auditservice.application.port.monitoring.MetricsQueryPort;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
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

    /** value = [thời điểm, "giá trị dạng chuỗi"] theo định dạng của Prometheus. */
    record Sample(Map<String, String> metric, List<Object> value) {}

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
