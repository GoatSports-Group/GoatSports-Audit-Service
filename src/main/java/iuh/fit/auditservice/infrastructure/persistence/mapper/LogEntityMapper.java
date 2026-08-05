package iuh.fit.auditservice.infrastructure.persistence.mapper;

import iuh.fit.auditservice.domain.Log;
import iuh.fit.auditservice.infrastructure.persistence.entity.LogEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LogEntityMapper {
    LogEntity createEntity(Log log);

    Log toDomain(LogEntity logEntity);
}
