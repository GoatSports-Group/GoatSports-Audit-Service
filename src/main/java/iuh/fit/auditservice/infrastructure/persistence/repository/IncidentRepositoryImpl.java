package iuh.fit.auditservice.infrastructure.persistence.repository;

import iuh.fit.auditservice.application.port.persistence.IncidentRepository;
import iuh.fit.auditservice.domain.Incident;
import iuh.fit.auditservice.infrastructure.persistence.entity.IncidentEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class IncidentRepositoryImpl implements IncidentRepository {
    private final MongoTemplate mongoTemplate;

    @Override
    public List<Incident> findOpen() {
        return mongoTemplate.find(new Query(Criteria.where("endedAt").is(null)), IncidentEntity.class).stream()
                .map(IncidentRepositoryImpl::toDomain).toList();
    }

    @Override
    public Incident save(Incident incident) {
        IncidentEntity saved = mongoTemplate.save(IncidentEntity.builder().id(incident.getId()).service(incident.getService())
                .status(incident.getStatus()).startedAt(incident.getStartedAt()).endedAt(incident.getEndedAt())
                .alertedAt(incident.getAlertedAt()).build());
        return toDomain(saved);
    }

    @Override
    public List<Incident> findRecent(Instant since, int limit) {
        Query query = new Query(new Criteria().orOperator(Criteria.where("startedAt").gte(since), Criteria.where("endedAt").is(null)))
                .with(Sort.by(Sort.Direction.DESC, "startedAt")).limit(limit);
        return mongoTemplate.find(query, IncidentEntity.class).stream().map(IncidentRepositoryImpl::toDomain).toList();
    }

    private static Incident toDomain(IncidentEntity entity) {
        return Incident.builder().id(entity.getId()).service(entity.getService()).status(entity.getStatus())
                .startedAt(entity.getStartedAt()).endedAt(entity.getEndedAt()).alertedAt(entity.getAlertedAt()).build();
    }
}
