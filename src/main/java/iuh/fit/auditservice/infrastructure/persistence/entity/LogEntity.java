package iuh.fit.auditservice.infrastructure.persistence.entity;

import iuh.fit.auditservice.domain.AuditChange;
import iuh.fit.auditservice.domain.AuditContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "audit_logs")
@CompoundIndex(name = "user_time_idx", def = "{'userId':1,'timestamp':-1}")
@CompoundIndex(name = "actor_time_idx", def = "{'actorId':1,'timestamp':-1}")
@CompoundIndex(name = "source_time_idx", def = "{'sourceService':1,'timestamp':-1}")
@CompoundIndex(name = "resource_time_idx", def = "{'resourceType':1,'resourceId':1,'timestamp':-1}")
public class LogEntity {
    @Id
    private String logId;

    /**
     * Sparse vì log API cũ không có eventId; unique để consumer retry không ghi trùng.
     */
    @Indexed(unique = true, sparse = true)
    private String eventId;

    private String eventType;
    @Indexed
    private String correlationId;
    private String sourceService;
    @Indexed
    private String actorId;
    private String resourceType;
    private String resourceId;

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

    private AuditContext context;
    private List<AuditChange> changes;
}
