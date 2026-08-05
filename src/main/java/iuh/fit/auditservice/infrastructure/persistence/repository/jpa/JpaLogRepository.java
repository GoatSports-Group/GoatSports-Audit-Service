package iuh.fit.auditservice.infrastructure.persistence.repository.jpa;

import iuh.fit.auditservice.infrastructure.persistence.entity.LogEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface JpaLogRepository extends MongoRepository<LogEntity, String> {
}
