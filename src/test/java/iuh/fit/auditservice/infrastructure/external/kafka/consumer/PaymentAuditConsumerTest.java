package iuh.fit.auditservice.infrastructure.external.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.auditservice.application.port.persistence.LogRepository;
import iuh.fit.auditservice.domain.Log;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentAuditConsumerTest {
    @Mock LogRepository logRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private PaymentAuditConsumer consumer() {
        return new PaymentAuditConsumer(objectMapper, logRepository);
    }

    @Test
    void paymentEventIsTracedWithEventAndCorrelationIdentity() {
        UUID eventId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        UUID referenceId = UUID.randomUUID();
        when(logRepository.saveIfAbsent(any(Log.class))).thenReturn(true);

        consumer().consume("""
                {
                  "eventId": "%s",
                  "eventType": "payment.succeeded.v1",
                  "producer": "payment-service",
                  "correlationId": "%s",
                  "occurredAt": "2026-09-19T10:15:30Z",
                  "payload": { "referenceType": "BOOKING", "referenceId": "%s", "status": "SUCCEEDED" }
                }
                """.formatted(eventId, correlationId, referenceId));

        ArgumentCaptor<Log> captor = ArgumentCaptor.forClass(Log.class);
        verify(logRepository).saveIfAbsent(captor.capture());

        Log saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId.toString());
        assertThat(saved.getCorrelationId()).isEqualTo(correlationId.toString());
        assertThat(saved.getSourceService()).isEqualTo("payment-service");
        assertThat(saved.getResourceType()).isEqualTo("BOOKING");
        assertThat(saved.getResourceId()).isEqualTo(referenceId.toString());
        assertThat(saved.getTimestamp()).isNotNull();
    }

    @Test
    void eventWithoutEventIdIsNotPersistedBecauseItCannotBeDeduped() {
        consumer().consume("""
                { "eventType": "payment.succeeded.v1", "payload": { "status": "SUCCEEDED" } }
                """);

        verify(logRepository, never()).saveIfAbsent(any());
        verify(logRepository, never()).save(any());
    }

    @Test
    void malformedMessageDoesNotPropagateAndPoisonTheConsumer() {
        consumer().consume("not-json");

        verify(logRepository, never()).saveIfAbsent(any());
    }
}
