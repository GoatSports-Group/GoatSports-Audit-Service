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
    private String userId;
    private String method;
    private String path;
    private String queryParams;
    private String ipAddress;
    private int statusCode;
    private long duration;
    private LocalDateTime timestamp;
    private String errorMessage;
}
