package iuh.fit.auditservice.infrastructure.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "audit_logs")
@CompoundIndex(
        name = "user_time_idx",
        def = "{'userId':1,'timestamp':-1}"
)
public class LogEntity {
    @Id
    private String logId;
    @Indexed
    private String userId;
    private String action;
    private String description;
    private String ipAddress;
    @Indexed
    private int statusCode;
    @Indexed
    private LocalDateTime timestamp;
    private String errorMessage;
}
