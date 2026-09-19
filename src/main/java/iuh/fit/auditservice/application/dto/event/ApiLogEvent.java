package iuh.fit.auditservice.application.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiLogEvent {
    private String eventId;
    private String correlationId;
    private String userId;
    private String action;
    private String description;
    private String ipAddress;
    private int statusCode;
    private LocalDateTime timestamp;
    private String errorMessage;
}
