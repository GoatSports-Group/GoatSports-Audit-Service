package iuh.fit.auditservice.presentation.response.log;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogResponse {
    private String logId;
    private String userId;
    private String action;
    private String description;
    private String ipAddress;
    private int statusCode;
    private LocalDateTime timestamp;
    private String errorMessage;
}
