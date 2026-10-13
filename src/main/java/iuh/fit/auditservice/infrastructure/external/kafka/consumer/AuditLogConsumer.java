package iuh.fit.auditservice.infrastructure.external.kafka.consumer;

import iuh.fit.auditservice.application.dto.event.ApiLogEvent;
import iuh.fit.auditservice.application.port.persistence.LogRepository;
import iuh.fit.auditservice.domain.AuditContext;
import iuh.fit.auditservice.domain.Log;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogConsumer {

    private final LogRepository logRepository;

    @KafkaListener(topics = "${spring.kafka.topic.user-log}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(ApiLogEvent event) {
        try {
            Log logDomain = Log.builder()
                    .eventId(event.getEventId())
                    .correlationId(event.getCorrelationId())
                    // Producer cũ không gửi tên service: giữ nhãn cũ "api-gateway".
                    .sourceService(StringUtils.hasText(event.getSourceService()) ? event.getSourceService() : "api-gateway")
                    .actorId(event.getUserId())
                    .userId(event.getUserId())
                    .action(event.getAction())
                    .description(event.getDescription())
                    .ipAddress(event.getIpAddress())
                    .statusCode(event.getStatusCode())
                    .timestamp(event.getTimestamp())
                    .errorMessage(event.getErrorMessage())
                    .context(AuditContext.builder()
                            .ipAddress(event.getIpAddress())
                            .statusCode(event.getStatusCode())
                            .httpMethod(event.getHttpMethod())
                            .requestUri(event.getRequestUri())
                            .userAgent(event.getUserAgent())
                            .build())
                    .build();

            if (!StringUtils.hasText(event.getEventId())) {
                // Producer cũ chưa gắn eventId: vẫn ghi, nhưng không dedupe được.
                logRepository.save(logDomain);
            } else if (!logRepository.saveIfAbsent(logDomain)) {
                log.debug("Bỏ qua audit log trùng eventId {}", event.getEventId());
                return;
            }

            log.debug("Saved audit log: {} {} (User: {}, Status: {})",
                    event.getAction(), event.getDescription(), event.getUserId(), event.getStatusCode());

        } catch (Exception e) {
            log.error("Failed to process audit log message: {}", e.getMessage(), e);
        }
    }
}
