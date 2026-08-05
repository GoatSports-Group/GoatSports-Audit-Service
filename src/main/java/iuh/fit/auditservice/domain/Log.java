package iuh.fit.auditservice.domain;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Log {
    private String logId;
    private String userId;
    private String action;
    private String description;
    private String ipAddress;
    private int statusCode;
    private LocalDateTime timestamp;
    private String errorMessage;
}
