package iuh.fit.auditservice.infrastructure.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "service_incidents")
public class IncidentEntity {
    @Id
    private String id;
    @Indexed
    private String service;
    private String status;
    @Indexed
    private Instant startedAt;
    /** null = sự cố còn mở. */
    @Indexed(sparse = true)
    private Instant endedAt;
    private Instant alertedAt;
}
