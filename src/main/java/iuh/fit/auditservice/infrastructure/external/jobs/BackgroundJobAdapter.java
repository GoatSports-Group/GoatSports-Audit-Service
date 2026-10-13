package iuh.fit.auditservice.infrastructure.external.jobs;

import iuh.fit.auditservice.application.dto.response.system.JobSummaryResponseCommand;
import iuh.fit.auditservice.application.port.monitoring.BackgroundJobPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Hỏi {@code GET /internal/jobs/summary} ở từng service sở hữu job nền. Service nào không trả lời thì vẫn có một
 * dòng "không gọi được" thay vì làm hỏng cả bảng.
 */
public class BackgroundJobAdapter implements BackgroundJobPort {
    /** Tên service -> base URL (vd report-service -> http://localhost:7074). */
    private final Map<String, String> services;
    private final RestClient restClient;

    public BackgroundJobAdapter(Map<String, String> services, String gatewaySecret) {
        this.services = services;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(4));
        this.restClient = RestClient.builder().requestFactory(requestFactory)
                .defaultHeader("X-Gateway-Secret", gatewaySecret).build();
    }

    /** Dạng các service trả về (bọc trong BaseResponse của từng service). */
    record Envelope(List<Item> data) {}

    record Item(String job, String label, long pending, long running, long failedLast24h, Instant oldestPendingAt) {}

    @Override
    public List<JobSummaryResponseCommand> summaries() {
        List<JobSummaryResponseCommand> result = new ArrayList<>();
        services.forEach((service, baseUrl) -> {
            try {
                Envelope envelope = restClient.get().uri(baseUrl + "/internal/jobs/summary").retrieve()
                        .body(new ParameterizedTypeReference<Envelope>() { });
                for (Item item : envelope == null || envelope.data() == null ? List.<Item>of() : envelope.data()) {
                    result.add(new JobSummaryResponseCommand(service, item.job(), item.label(), item.pending(), item.running(),
                            item.failedLast24h(), item.oldestPendingAt(), true, null));
                }
            } catch (RestClientException exception) {
                result.add(new JobSummaryResponseCommand(service, null, null, 0, 0, 0, null, false,
                        "Không gọi được " + service));
            }
        });
        return result;
    }
}
