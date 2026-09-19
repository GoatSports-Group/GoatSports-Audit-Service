package iuh.fit.auditservice.infrastructure.external.kafka.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.auditservice.application.port.persistence.LogRepository;
import iuh.fit.auditservice.domain.Log;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Ghi audit trail cho luồng tiền (payment/refund) theo contract DomainEventEnvelope.
 * Payload gốc không được lưu vì có thể chứa PII/thông tin tài khoản; chỉ lưu định danh truy vết.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "payment.events",
        name = "audit-consumer-enabled",
        havingValue = "true"
)
public class PaymentAuditConsumer {
    private final ObjectMapper objectMapper;
    private final LogRepository logRepository;

    @KafkaListener(
            topics = "${payment.events.topic}",
            groupId = "${payment.events.audit-group-id}",
            containerFactory = "stringListenerContainerFactory"
    )
    public void consume(String message) {
        try {
            JsonNode envelope = objectMapper.readTree(message);
            String eventId = text(envelope, "eventId");
            if (eventId == null) {
                log.warn("Bỏ qua payment event không có eventId: {}", envelope.path("eventType").asText());
                return;
            }

            JsonNode payload = envelope.path("payload");
            Log auditLog = Log.builder()
                    .eventId(eventId)
                    .eventType(text(envelope, "eventType"))
                    .correlationId(text(envelope, "correlationId"))
                    .sourceService(text(envelope, "producer"))
                    .actorId(text(envelope, "actorId"))
                    .resourceType(text(payload, "referenceType"))
                    .resourceId(text(payload, "referenceId"))
                    .userId(text(payload, "payerId"))
                    .action(text(envelope, "eventType"))
                    .description("aggregateId=" + envelope.path("aggregateId").asText("")
                            + ", status=" + payload.path("status").asText(""))
                    .timestamp(occurredAt(envelope))
                    .build();

            if (!logRepository.saveIfAbsent(auditLog)) {
                log.debug("Bỏ qua payment audit trùng eventId {}", eventId);
            }
        } catch (Exception exception) {
            log.error("Không xử lý được payment event cho audit: {}", exception.getMessage(), exception);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private LocalDateTime occurredAt(JsonNode envelope) {
        String raw = text(envelope, "occurredAt");
        if (raw == null) return LocalDateTime.now();
        try {
            return LocalDateTime.ofInstant(Instant.parse(raw), ZoneId.systemDefault());
        } catch (Exception ignored) {
            return LocalDateTime.now();
        }
    }
}
