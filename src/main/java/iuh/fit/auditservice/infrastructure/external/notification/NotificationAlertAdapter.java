package iuh.fit.auditservice.infrastructure.external.notification;

import iuh.fit.auditservice.application.port.notification.AlertPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/** Cảnh báo admin qua notification-service ({@code POST /internal/notifications/admin}, gateway chặn đường này từ ngoài). */
@Slf4j
public class NotificationAlertAdapter implements AlertPort {
    private final RestClient restClient;

    public NotificationAlertAdapter(String notificationServiceUrl, String gatewaySecret) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().baseUrl(notificationServiceUrl).requestFactory(requestFactory)
                .defaultHeader("X-Gateway-Secret", gatewaySecret).build();
    }

    @Override
    public void notifyAdmin(UUID eventId, String title, String content, String referenceType, UUID referenceId) {
        try {
            restClient.post().uri("/internal/notifications/admin")
                    .body(Map.of("eventId", eventId, "title", title, "content", content, "sourceService", "AUDIT",
                            "referenceType", referenceType, "referenceId", referenceId))
                    .retrieve().toBodilessEntity();
        } catch (RestClientException exception) {
            // Không làm hỏng lượt theo dõi: lượt sau gửi lại (eventId giữ nguyên nên không trùng).
            log.warn("Không gửi được cảnh báo '{}' tới notification-service: {}", title, exception.getMessage());
        }
    }
}
