package iuh.fit.auditservice.infrastructure.external.kafka.consumer;

import iuh.fit.auditservice.application.dto.event.ApiLogEvent;
import iuh.fit.auditservice.application.port.persistence.LogRepository;
import iuh.fit.auditservice.domain.Log;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogConsumer {

    private final LogRepository logRepository;

    @KafkaListener(
            topics = "${spring.kafka.topic.user-log}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(ApiLogEvent event) {
        try {
            Log log = Log.builder()
                    .userId(event.getUserId())
                    .method(event.getMethod())
                    .path(event.getPath())
                    .queryParams(event.getQueryParams())
                    .ipAddress(event.getIpAddress())
                    .statusCode(event.getStatusCode())
                    .duration(event.getDuration())
                    .timestamp(event.getTimestamp().atZone(ZoneId.systemDefault()).toInstant())
                    .errorMessage(event.getErrorMessage())
                    .build();

            logRepository.save(log);

            AuditLogConsumer.log.debug("Saved audit log: {} {} (User: {}, Status: {})",
                    event.getMethod(), event.getPath(), event.getUserId(), event.getStatusCode());

        } catch (Exception e) {
            log.error("Failed to process audit log message: {}", e.getMessage(), e);
        }
    }
}
